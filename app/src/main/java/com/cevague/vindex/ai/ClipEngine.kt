package com.cevague.vindex.ai

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.data.repository.AiModelRepository
import com.cevague.vindex.search.normalizeL2
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Moteur d'inférence CLIP (phase 2 §4.2-4.3) : sessions ONNX paresseuses sur le
 * modèle actif de type `clip`, rechargées si l'utilisateur change de modèle.
 * CPU uniquement (contrainte F-Droid). Les noms d'entrées/sorties sont
 * découverts dynamiquement pour tolérer les différents exports ONNX
 * (input_ids/attention_mask int32 ou int64, sortie `*_embeds` ou unique).
 *
 * Tous les vecteurs renvoyés sont normalisés L2 (le stockage et la similarité
 * l'exigent, cf. VectorUtils).
 */
@Singleton
class ClipEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiModelRepository: AiModelRepository
) {

    data class ActiveClip(
        val modelName: String,
        val embeddingDim: Int,
        val languages: List<String>,
        val similarityFloor: Float?
    )

    private class Loaded(
        val model: AiModel,
        val config: ModelConfig,
        val dir: File
    ) {
        var imageSession: OrtSession? = null
        var textSession: OrtSession? = null
        var tokenizer: TextTokenizer? = null
        var pixelBuffer: IntArray? = null
        var floatBuffer: FloatArray? = null

        fun close() {
            imageSession?.close()
            textSession?.close()
            imageSession = null
            textSession = null
        }
    }

    private val env: OrtEnvironment get() = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()
    private var loaded: Loaded? = null

    /** Modèle CLIP actif, ou null si aucun n'est importé/activé. */
    suspend fun activeClip(): ActiveClip? = mutex.withLock {
        ensureLoadedLocked()?.let {
            ActiveClip(
                it.model.modelName,
                it.config.embeddingDim ?: 0,
                it.config.languages,
                it.config.similarityFloor
            )
        }
    }

    /** Encode une requête texte ; null si aucun modèle actif. */
    suspend fun encodeText(text: String): FloatArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = ensureLoadedLocked() ?: return@withLock null
            val session = loaded.textSession ?: openSession(loaded, ModelConfig.FILE_TEXT_ENCODER)
                .also { loaded.textSession = it }
            val tokenizer = loaded.tokenizer ?: createTokenizer(loaded)
                .also { loaded.tokenizer = it }

            val prompt = loaded.config.promptTemplate?.replace("{}", text) ?: text
            val encoded = tokenizer.encode(prompt)

            val inputs = mutableMapOf<String, OnnxTensor>()
            try {
                val idsName = session.inputName("input_ids")
                    ?: session.inputInfo.keys.first()

                inputs[idsName] = intTensor(session, idsName, encoded.ids)
                session.inputName("attention_mask")?.let { maskName ->
                    inputs[maskName] = intTensor(session, maskName, encoded.attentionMask)
                }

                session.run(inputs).use { result ->
                    extractEmbedding(result, loaded.config.embeddingDim, isText = true)
                }
            } finally {
                inputs.values.forEach { it.close() }
            }
        }
    }

    /**
     * Encode une photo (URI content://) ; null si aucun modèle actif. Les
     * échecs de décodage/inférence remontent en exception (gérés par photo
     * dans le worker).
     */
    suspend fun encodeImage(contentUri: String): FloatArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = ensureLoadedLocked() ?: return@withLock null
            val size = loaded.config.inputSize ?: error("input_size manquant")
            val bitmap = loadBitmap(contentUri, size, loaded.config.resizeMode)
            val session = loaded.imageSession ?: openSession(loaded, ModelConfig.FILE_IMAGE_ENCODER)
                .also { loaded.imageSession = it }

            val tensor = bitmapToTensor(loaded, bitmap, size)
            try {
                val inputName = session.inputName("pixel_values") ?: session.inputInfo.keys.first()
                session.run(mapOf(inputName to tensor)).use { result ->
                    extractEmbedding(result, loaded.config.embeddingDim, isText = false)
                }
            } finally {
                tensor.close()
            }
        }
    }

    /**
     * Libère les sessions (fin de worker, pression mémoire) : l'encodeur image
     * pèse plusieurs centaines de Mo mappés. Rechargées paresseusement au
     * prochain appel.
     */
    suspend fun releaseSessions() = mutex.withLock {
        loaded?.close()
    }

    /** Charge le modèle et les sessions texte/tokenizer en avance. */
    suspend fun preload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = ensureLoadedLocked() ?: return@withLock
            if (loaded.textSession == null) {
                loaded.textSession = openSession(loaded, ModelConfig.FILE_TEXT_ENCODER)
            }
            if (loaded.tokenizer == null) {
                loaded.tokenizer = createTokenizer(loaded)
            }
        }
    }

    // ------------------------------------------------------------------ privé

    private suspend fun ensureLoadedLocked(): Loaded? {
        val active = aiModelRepository.getActiveModelOnce(AiModel.TYPE_CLIP)
        if (active == null || active.modelPath == null || active.configJson == null) {
            loaded?.close()
            loaded = null
            return null
        }
        val current = loaded
        if (current != null && current.model.id == active.id) return current

        current?.close()
        val config = ModelConfig.parse(active.configJson)
        return Loaded(active, config, File(active.modelPath)).also { loaded = it }
    }

    /** Tokenizer déclaré par la config (`text.tokenizer`). */
    private fun createTokenizer(loaded: Loaded): TextTokenizer {
        val config = loaded.config
        val contextLength = config.contextLength ?: 77
        return when (config.tokenizer) {
            ModelConfig.TOKENIZER_CLIP_BPE -> ClipBpeTokenizer.fromFiles(
                File(loaded.dir, config.files.getValue(ModelConfig.FILE_TOKENIZER_VOCAB)),
                File(loaded.dir, config.files.getValue(ModelConfig.FILE_TOKENIZER_MERGES)),
                contextLength
            )
            ModelConfig.TOKENIZER_SENTENCEPIECE -> SentencePieceBpeTokenizer.fromTokenizerJson(
                File(loaded.dir, config.files.getValue(ModelConfig.FILE_TOKENIZER_JSON)),
                contextLength
            )
            else -> error("tokenizer non supporté : ${config.tokenizer}")
        }
    }

    private fun openSession(loaded: Loaded, fileRole: String): OrtSession {
        val file = File(loaded.dir, loaded.config.files.getValue(fileRole))
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(min(4, Runtime.getRuntime().availableProcessors()))
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

    /**
     * [resizeMode] : `center_crop` (CLIP — recadrage centré) ou `squash`
     * (SigLIP — image entière écrasée en carré, ratio non conservé, comme à
     * l'entraînement). Glide gère l'orientation EXIF dans les deux cas.
     */
    private fun loadBitmap(contentUri: String, size: Int, resizeMode: String): Bitmap {
        val request = Glide.with(context)
            .asBitmap()
            .load(Uri.parse(contentUri))
            .format(DecodeFormat.PREFER_ARGB_8888)
            .disallowHardwareConfig()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)

        if (resizeMode != ModelConfig.RESIZE_SQUASH) {
            return request.centerCrop().submit(size, size).get()
        }
        
        // Squash : décodage borné à ~size (Glide sous-échantillonne en gardant
        // le ratio — jamais la pleine résolution en mémoire), puis étirement
        // final en carré. fitCenter garantit un bitmap ≤ size × size.
        val raw = request.fitCenter().submit(size, size).get()
        if (raw.width == size && raw.height == size) return raw
        return Bitmap.createScaledBitmap(raw, size, size, true)
    }

    /** Bitmap → tenseur CHW normalisé mean/std ; buffers réutilisés entre photos. */
    private fun bitmapToTensor(loaded: Loaded, bitmap: Bitmap, size: Int): OnnxTensor {
        val area = size * size
        val pixels = loaded.pixelBuffer?.takeIf { it.size == area }
            ?: IntArray(area).also { loaded.pixelBuffer = it }
        val floats = loaded.floatBuffer?.takeIf { it.size == 3 * area }
            ?: FloatArray(3 * area).also { loaded.floatBuffer = it }

        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        val config = loaded.config
        val mean = config.mean ?: error("image.mean manquant")
        val std = config.std ?: error("image.std manquant")

        val m0 = mean[0].toFloat(); val m1 = mean[1].toFloat(); val m2 = mean[2].toFloat()
        val s0 = std[0].toFloat(); val s1 = std[1].toFloat(); val s2 = std[2].toFloat()

        for (i in 0 until area) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            floats[i] = (r - m0) / s0
            floats[area + i] = (g - m1) / s1
            floats[2 * area + i] = (b - m2) / s2
        }
        return OnnxTensor.createTensor(
            env, FloatBuffer.wrap(floats), longArrayOf(1, 3, size.toLong(), size.toLong())
        )
    }

    /**
     * Sélection de la sortie embedding, validée par le banc PC (SiglipBenchPc) :
     * UNIQUEMENT parmi les tenseurs 2D `[1, dim]` — jamais un 3D. Le piège des
     * exports HF deux-tours : `last_hidden_state` [1, seq, dim] arrive en
     * premier, et en lire les premiers floats donne l'embedding d'UN token →
     * scores faibles et incohérents. La bonne sortie est `*_embeds` (exports
     * projetés type CLIP/Qdrant) ou `pooler_output` (exports HF type SigLIP,
     * qui contient bien la tête finale).
     */
    private fun extractEmbedding(result: OrtSession.Result, expectedDim: Int?, isText: Boolean): FloatArray {
        val preferredName = if (isText) "text_embeds" else "image_embeds"
        val candidates = result.mapNotNull { entry ->
            val tensor = entry.value as? OnnxTensor ?: return@mapNotNull null
            if (tensor.info.shape.size == 2) entry.key to tensor else null
        }
        val (name, tensor) = candidates.firstOrNull { it.first == preferredName }
            ?: candidates.firstOrNull { it.first.contains("embeds") }
            ?: candidates.firstOrNull { it.first.contains("pooler") }
            ?: candidates.firstOrNull {
                expectedDim == null || it.second.info.shape.last().toInt() == expectedDim
            }
            ?: error(
                "aucune sortie embedding 2D [1, dim] — sorties : " +
                    result.joinToString { "${it.key}${(it.value as? OnnxTensor)?.info?.shape?.contentToString()}" }
            )

        val dim = tensor.info.shape.last().toInt()
        if (expectedDim != null && dim != expectedDim) {
            error("dimension $dim != embedding_dim $expectedDim (sortie $name)")
        }
        val out = FloatArray(dim)
        tensor.floatBuffer.get(out)
        return out.normalizeL2()
    }
}
