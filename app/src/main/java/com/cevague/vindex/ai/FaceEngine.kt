package com.cevague.vindex.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.data.repository.AiModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Moteur visages (phase 3) : détection puis embedding, sur les modèles actifs
 * des types `face_detection` (YuNet) et `face_embedding` (EdgeFace). Les deux
 * sont requis — un détecteur sans embedder, ou l'inverse, ne produit rien.
 *
 * Sessions ONNX paresseuses, rechargées si l'utilisateur change de modèle,
 * libérées par [releaseSessions] en fin de worker. CPU uniquement (F-Droid).
 *
 * Les deux modèles ont des conventions d'entrée **opposées**, dictées par leur
 * lignée : le détecteur (OpenCV) attend du BGR brut 0-255, l'embedder (PyTorch)
 * du RGB normalisé mean/std. Les maths du post-traitement vivent dans
 * `FaceDecoder` (pures, testées).
 */
@Singleton
class FaceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiModelRepository: AiModelRepository
) {

    /** Noms des modèles actifs (marqueur `photo_analyses`, `faces.embedding_model`). */
    data class ActiveFace(
        val detectorModel: String,
        val embedderModel: String,
        val embeddingDim: Int
    )

    /**
     * Photo préparée pour le détecteur : le contenu, ratio conservé, est collé
     * en haut-gauche d'un canevas carré ; le reste est noir. [contentWidth] et
     * [contentHeight] mesurent ce contenu — diviser une coordonnée du canevas
     * par eux donne directement la coordonnée normalisée 0-1 dans la photo.
     */
    private data class Letterboxed(
        val bitmap: Bitmap,
        val contentWidth: Int,
        val contentHeight: Int
    )

    private class Loaded(
        val model: AiModel,
        val config: ModelConfig,
        val dir: File
    ) {
        var session: OrtSession? = null
        var pixelBuffer: IntArray? = null
        var floatBuffer: FloatArray? = null

        fun close() {
            session?.close()
            session = null
        }
    }

    private val env: OrtEnvironment get() = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()
    private var loadedDetector: Loaded? = null
    private var loadedEmbedder: Loaded? = null

    /** Modèles visages actifs, ou null si l'un des deux manque. */
    suspend fun activeFace(): ActiveFace? = mutex.withLock {
        val detector = ensureDetectorLocked() ?: return@withLock null
        val embedder = ensureEmbedderLocked() ?: return@withLock null
        ActiveFace(
            detectorModel = detector.model.modelName,
            embedderModel = embedder.model.modelName,
            embeddingDim = embedder.config.embeddingDim ?: 0
        )
    }

    /**
     * Visages d'une photo, boîtes et landmarks normalisés 0-1 ; liste vide si
     * aucun modèle de détection n'est actif. Les échecs de décodage/inférence
     * remontent en exception (gérés par photo dans le worker).
     */
    suspend fun locateFaces(contentUri: String): List<DetectedFace> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = ensureDetectorLocked() ?: return@withLock emptyList()
            val detection = loaded.config.detection ?: error("detection manquant")
            val size = loaded.config.inputSize ?: error("input_size manquant")

            val letterboxed = loadForDetection(contentUri, size)
            val session = loaded.session
                ?: openSession(loaded, ModelConfig.FILE_FACE_DETECTOR).also { loaded.session = it }

            val tensor = detectorTensor(loaded, letterboxed.bitmap)
            try {
                val inputName = session.inputInfo.keys.first()
                session.run(mapOf(inputName to tensor)).use { result ->
                    val outputs = result.associate { it.key to it.value as OnnxTensor }
                    nms(decodeStrides(outputs, detection, size), detection.nmsThreshold)
                        .filterMinSize(detection.minFaceSize)
                        .toDetectedFaces(letterboxed.contentWidth, letterboxed.contentHeight)
                }
            } finally {
                tensor.close()
            }
        }
    }

    /** Libère les sessions (fin de worker, pression mémoire). */
    suspend fun releaseSessions() = mutex.withLock {
        loadedDetector?.close()
        loadedEmbedder?.close()
    }

    // ------------------------------------------------------------------ privé

    private suspend fun ensureDetectorLocked(): Loaded? =
        ensureLoadedLocked(AiModel.TYPE_FACE_DETECTION, loadedDetector) { loadedDetector = it }

    private suspend fun ensureEmbedderLocked(): Loaded? =
        ensureLoadedLocked(AiModel.TYPE_FACE_EMBEDDING, loadedEmbedder) { loadedEmbedder = it }

    /**
     * Cache du modèle actif de [type] : réutilise [current] tant que
     * l'utilisateur n'a pas changé de modèle, sinon reconstruit le holder (la
     * session, elle, reste paresseuse). [assign] écrit le champ de cache de
     * l'appelant.
     */
    private suspend fun ensureLoadedLocked(
        type: String,
        current: Loaded?,
        assign: (Loaded?) -> Unit
    ): Loaded? {
        val active = aiModelRepository.getActiveModelOnce(type)
        if (active == null || active.modelPath == null || active.configJson == null) {
            current?.close()
            assign(null)
            return null
        }
        if (current != null && current.model.id == active.id) return current

        current?.close()
        val config = ModelConfig.parse(active.configJson)
        return Loaded(active, config, File(active.modelPath)).also(assign)
    }

    private fun openSession(loaded: Loaded, fileRole: String): OrtSession {
        val file = File(loaded.dir, loaded.config.files.getValue(fileRole))
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(min(4, Runtime.getRuntime().availableProcessors()))
        }
        return env.createSession(file.absolutePath, options)
    }

    /**
     * Recopie les sorties de chaque stride hors des tenseurs — ils sont fermés
     * avec le `Result` — puis délègue les maths à `decodeStride`. Les noms sont
     * reconstruits depuis la config (`cls_8`, `bbox_16`…), rien n'est en dur.
     */
    private fun decodeStrides(
        outputs: Map<String, OnnxTensor>,
        detection: DetectionConfig,
        size: Int
    ): List<Candidate> = detection.strides.flatMap { stride ->
        val cols = size / stride
        val anchors = cols * cols
        val cls = FloatArray(anchors)
        val obj = FloatArray(anchors)
        val bbox = FloatArray(anchors * 4)
        val kps = FloatArray(anchors * 10)

        outputs.getValue("${detection.classLayer}_$stride").floatBuffer.get(cls)
        outputs.getValue("${detection.objectLayer}_$stride").floatBuffer.get(obj)
        outputs.getValue("${detection.boxLayer}_$stride").floatBuffer.get(bbox)
        outputs.getValue("${detection.landmarkLayer}_$stride").floatBuffer.get(kps)

        decodeStride(cls, obj, bbox, kps, stride, cols, detection.scoreThreshold)
    }

    /**
     * Décode la photo à la taille du détecteur en conservant le ratio (Glide
     * applique l'orientation EXIF), puis la colle en haut-gauche d'un canevas
     * carré. Padding bas/droite : c'est la convention de YuNet, et ça rend la
     * reprojection triviale (aucun offset à défaire). Jamais de décodage en
     * pleine résolution.
     */
    private fun loadForDetection(contentUri: String, size: Int): Letterboxed {
        val fitted = Glide.with(context)
            .asBitmap()
            .load(contentUri.toUri())
            .format(DecodeFormat.PREFER_ARGB_8888)
            .disallowHardwareConfig()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .fitCenter()
            .submit(size, size)
            .get()

        // Bitmap neuf = zéros = padding noir.
        val padded = createBitmap(size, size)
        Canvas(padded).drawBitmap(fitted, 0f, 0f, null)

        return Letterboxed(padded, fitted.width, fitted.height)
    }

    /** Canevas → tenseur CHW en BGR brut 0-255 (convention YuNet). */
    private fun detectorTensor(loaded: Loaded, bitmap: Bitmap): OnnxTensor {
        val size = bitmap.width
        val area = size * size
        val pixels = loaded.pixelBuffer?.takeIf { it.size == area }
            ?: IntArray(area).also { loaded.pixelBuffer = it }
        val floats = loaded.floatBuffer?.takeIf { it.size == 3 * area }
            ?: FloatArray(3 * area).also { loaded.floatBuffer = it }

        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        for (i in 0 until area) {
            val p = pixels[i]
            floats[i] = (p and 0xFF).toFloat()
            floats[area + i] = (p shr 8 and 0xFF).toFloat()
            floats[2 * area + i] = (p shr 16 and 0xFF).toFloat()
        }
        return OnnxTensor.createTensor(
            env, FloatBuffer.wrap(floats), longArrayOf(1, 3, size.toLong(), size.toLong())
        )
    }
}
