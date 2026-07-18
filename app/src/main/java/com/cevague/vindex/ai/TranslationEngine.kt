package com.cevague.vindex.ai

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import android.util.LruCache
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.data.repository.AiModelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.IntBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Traduction de requêtes hors-ligne (spec §4.6) : modèle seq2seq Marian/OPUS-MT
 * exporté en **deux fichiers ONNX** (encodeur + décodeur sans cache KV), décodé
 * en **greedy** ici même — ONNX Runtime n'exécute qu'un pas de génération, la
 * boucle auto-régressive est à nous.
 *
 * Choix assumé face au guide (GUIDE_TRADUCTION_OFFLINE.md, option C) : depuis
 * l'écriture du guide, l'app a déjà les patrons nécessaires (tokenizers maison
 * testés, découverte dynamique des E/S, import générique) — l'option ONNX est
 * devenue la moins chère et n'ajoute **aucune dépendance ni NDK**. Un backend
 * Mozilla/slimt (modèles ~17 Mo) reste possible plus tard derrière la même
 * couture : le pipeline ne connaît que `translate()`.
 *
 * Décodage sans cache KV : le décodeur relit tout le préfixe à chaque pas —
 * O(n²), négligeable pour des requêtes de quelques mots, et évite les dizaines
 * d'entrées `past_key_values` des exports « merged » (fragiles d'un export à
 * l'autre).
 */
@Singleton
class TranslationEngine @Inject constructor(
    private val aiModelRepository: AiModelRepository
) {

    data class ActiveTranslation(
        val modelName: String,
        val sourceLanguages: List<String>,
        val targetLanguage: String
    )

    private class Loaded(
        val model: AiModel,
        val config: ModelConfig,
        val dir: File
    ) {
        var encoderSession: OrtSession? = null
        var decoderSession: OrtSession? = null
        var tokenizer: SentencePieceUnigramTokenizer? = null

        fun close() {
            encoderSession?.close()
            decoderSession?.close()
            encoderSession = null
            decoderSession = null
        }
    }

    private val env: OrtEnvironment get() = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()
    private var loaded: Loaded? = null

    /**
     * Requête → traduction, clé préfixée par l'id du modèle (changer de modèle
     * ne doit pas resservir les traductions de l'ancien). Les requêtes se
     * répètent beaucoup ; la traduction est l'étape la plus lente du chemin
     * interactif.
     */
    private val cache = LruCache<String, String>(CACHE_SIZE)

    /** Modèle de traduction actif, ou null si aucun. */
    suspend fun activeTranslation(): ActiveTranslation? = mutex.withLock {
        val current = ensureLoadedLocked() ?: return@withLock null
        val translation = current.config.translation ?: return@withLock null
        ActiveTranslation(
            current.model.modelName,
            translation.sourceLanguages,
            translation.targetLanguage
        )
    }

    /**
     * Traduit [text] depuis [sourceLanguage] ; null si aucun modèle actif, si la
     * paire n'est pas couverte (source non comprise, ou cible hors
     * [acceptedTargets] quand la liste est fournie), ou si le décodage ne
     * produit rien — l'appelant retombe alors sur le texte d'origine.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        acceptedTargets: List<String> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = ensureLoadedLocked() ?: return@withLock null
            val translation = current.config.translation ?: return@withLock null
            if (translation.sourceLanguages.none { it.equals(sourceLanguage, true) }) {
                return@withLock null
            }
            if (acceptedTargets.isNotEmpty() &&
                acceptedTargets.none { it.equals(translation.targetLanguage, true) }
            ) {
                return@withLock null
            }

            val cacheKey = "${current.model.id}|$text"
            cache.get(cacheKey)?.let { return@withLock it }

            val result = try {
                decodeGreedy(current, translation, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Une panne du traducteur ne doit jamais tuer la recherche :
                // null = l'appelant encode le texte brut et signale le mode
                // dégradé, au lieu de faire remonter l'exception jusqu'au crash.
                Log.e(TAG, "Traduction échouée ($sourceLanguage → ${translation.targetLanguage})", e)
                null
            }
            val cleaned = result?.takeIf { it.isNotBlank() } ?: return@withLock null
            Log.d(TAG, "« $text » ($sourceLanguage) → « $cleaned »")
            cache.put(cacheKey, cleaned)
            cleaned
        }
    }

    /** Libère les sessions (pression mémoire, fin d'usage) ; rechargées au besoin. */
    suspend fun releaseSessions() = mutex.withLock {
        loaded?.close()
    }

    // ------------------------------------------------------------------ privé

    private suspend fun ensureLoadedLocked(): Loaded? {
        val active = aiModelRepository.getActiveModelOnce(AiModel.TYPE_TRANSLATION)
        if (active == null || active.modelPath == null || active.configJson == null) {
            loaded?.close()
            loaded = null
            return null
        }
        val current = loaded
        if (current != null && current.model.id == active.id) return current

        current?.close()
        cache.evictAll()
        val config = ModelConfig.parse(active.configJson)
        return Loaded(active, config, File(active.modelPath)).also { loaded = it }
    }

    private fun decodeGreedy(
        current: Loaded,
        translation: TranslationConfig,
        text: String
    ): String? {
        val tokenizer = current.tokenizer
            ?: SentencePieceUnigramTokenizer.fromTokenizerJson(
                File(current.dir, current.config.files.getValue(ModelConfig.FILE_TOKENIZER_JSON))
            ).also { current.tokenizer = it }

        val sourceIds = tokenizer.encode(text)
        if (sourceIds.isEmpty()) return null
        // Convention Marian : la séquence source se termine par </s>.
        val sourceWithEos = LongArray(sourceIds.size + 1) { i ->
            if (i < sourceIds.size) sourceIds[i].toLong() else translation.eosTokenId.toLong()
        }

        val encoder = current.encoderSession
            ?: openSession(current, ModelConfig.FILE_TRANSLATION_ENCODER)
                .also { current.encoderSession = it }
        val decoder = current.decoderSession
            ?: openSession(current, ModelConfig.FILE_TRANSLATION_DECODER)
                .also { current.decoderSession = it }

        val attentionMask = LongArray(sourceWithEos.size) { 1L }

        // --- Encodeur : une passe. Sa sortie [1, seq, hidden] est passée TELLE
        // QUELLE au décodeur, et le Result reste donc vivant pendant tout le
        // décodage : la recopier dans un tenseur float32 neuf cassait les
        // exports fp16 — le décodeur exige le dtype exact de l'export
        // (float16), et rejetait sa première entrée. L'encodeur « marchait »,
        // le décodeur jamais.
        val generated = mutableListOf(translation.decoderStartTokenId)
        val encoderInputs = mutableMapOf<String, OnnxTensor>()
        var encoderResult: OrtSession.Result? = null
        try {
            val idsName = encoder.inputName("input_ids") ?: encoder.inputInfo.keys.first()
            encoderInputs[idsName] = intTensor(encoder, idsName, sourceWithEos)
            encoder.inputName("attention_mask")?.let { maskName ->
                encoderInputs[maskName] = intTensor(encoder, maskName, attentionMask)
            }
            val result = encoder.run(encoderInputs).also { encoderResult = it }
            val (_, hiddenTensor) = firstTensorOfRank(result, 3)
                ?: error("l'encodeur ne renvoie aucune sortie [1, seq, hidden]")

            // --- Décodeur : greedy, argmax du dernier pas, arrêt sur eos.
            val decoderIdsName = decoder.inputInfo.keys
                .firstOrNull { it.contains("input_ids") && !it.contains("encoder") }
                ?: error("entrée input_ids du décodeur introuvable")
            val hiddenName = decoder.inputName("encoder_hidden")
                ?: error("entrée encoder_hidden_states du décodeur introuvable")
            val encoderMaskName = decoder.inputName("encoder_attention")

            while (generated.size <= translation.maxOutputTokens) {
                val stepInputs = mutableMapOf<String, OnnxTensor>()
                val next: Int
                try {
                    stepInputs[decoderIdsName] = intTensor(
                        decoder, decoderIdsName,
                        LongArray(generated.size) { generated[it].toLong() }
                    )
                    stepInputs[hiddenName] = hiddenTensor
                    encoderMaskName?.let {
                        stepInputs[it] = intTensor(decoder, it, attentionMask)
                    }
                    next = decoder.run(stepInputs).use { stepResult ->
                        val (_, logits) = firstTensorOfRank(stepResult, 3, preferred = "logits")
                            ?: error("le décodeur ne renvoie aucune sortie logits")
                        argmaxLastStep(logits, generated.size)
                    }
                } finally {
                    // Le tenseur des états cachés appartient au Result de
                    // l'encodeur : il ne se ferme qu'avec lui, pas par pas.
                    stepInputs.values.forEach { if (it !== hiddenTensor) it.close() }
                }
                if (next == translation.eosTokenId) break
                generated.add(next)
            }
        } finally {
            encoderInputs.values.forEach { it.close() }
            encoderResult?.close()
        }

        val skip = buildSet {
            add(translation.decoderStartTokenId)
            add(translation.eosTokenId)
            tokenizer.idOf("<unk>")?.let { add(it) }
            tokenizer.idOf("<pad>")?.let { add(it) }
        }
        return tokenizer.decode(generated, skip)
    }

    /** Argmax du vocabulaire à la position [step - 1] d'une sortie [1, steps, vocab]. */
    private fun argmaxLastStep(logits: OnnxTensor, step: Int): Int {
        val vocab = logits.info.shape.last().toInt()
        val buffer = logits.floatBuffer
        val offset = (step - 1) * vocab
        var bestId = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until vocab) {
            val score = buffer.get(offset + i)
            if (score > bestScore) {
                bestScore = score
                bestId = i
            }
        }
        return bestId
    }

    private fun firstTensorOfRank(
        result: OrtSession.Result,
        rank: Int,
        preferred: String? = null
    ): Pair<String, OnnxTensor>? {
        val candidates = result.mapNotNull { entry ->
            val tensor = entry.value as? OnnxTensor ?: return@mapNotNull null
            if (tensor.info.shape.size == rank) entry.key to tensor else null
        }
        return preferred?.let { fragment -> candidates.firstOrNull { it.first.contains(fragment) } }
            ?: candidates.firstOrNull()
    }

    private fun openSession(current: Loaded, fileRole: String): OrtSession {
        val file = File(current.dir, current.config.files.getValue(fileRole))
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(min(4, Runtime.getRuntime().availableProcessors()))
            // BASIC et non ALL (défaut) : bug d'ORT 1.27 mesuré sur les exports
            // Marian fp16 — la fusion SimplifiedLayerNormFusion (niveau extended)
            // référence un cast InsertedPrecisionFreeCast_* disparu et la session
            // refuse de s'initialiser (ORT_FAIL). Reproduit sur PC (même version) :
            // ALL échoue, BASIC traduit correctement en ~0,5 s/requête.
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        return env.createSession(file.absolutePath, options)
    }

    private fun OrtSession.inputName(fragment: String): String? =
        inputInfo.keys.firstOrNull { it.contains(fragment) }

    /** Tenseur d'entiers au type attendu par le modèle (int64 ou int32). */
    private fun intTensor(session: OrtSession, inputName: String, values: LongArray): OnnxTensor {
        val info = session.inputInfo.getValue(inputName).info as TensorInfo
        val shape = longArrayOf(1, values.size.toLong())
        return if (info.type == OnnxJavaType.INT32) {
            val ints = IntArray(values.size) { values[it].toInt() }
            OnnxTensor.createTensor(env, IntBuffer.wrap(ints), shape)
        } else {
            OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)
        }
    }

    private companion object {
        const val TAG = "TranslationEngine"
        const val CACHE_SIZE = 50
    }
}
