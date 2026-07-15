package com.cevague.vindex.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
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

    data class AnalyzedFace(val detected: DetectedFace, val embedding: FloatArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AnalyzedFace

            if (detected != other.detected) return false
            if (!embedding.contentEquals(other.embedding)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = detected.hashCode()
            result = 31 * result + embedding.contentHashCode()
            return result
        }
    }

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

            val tensor = imageTensor(loaded, letterboxed.bitmap)
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

    private fun decodeFitted(contentUri: String, size: Int): Bitmap =
        Glide.with(context).asBitmap().load(contentUri.toUri())
            .format(DecodeFormat.PREFER_ARGB_8888)
            .disallowHardwareConfig()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .fitCenter().submit(size, size).get()

    private fun loadForDetection(contentUri: String, size: Int): Letterboxed {
        val fitted = decodeFitted(contentUri, size)
        val padded = createBitmap(size, size)
        Canvas(padded).drawBitmap(fitted, 0f, 0f, null)
        return Letterboxed(padded, fitted.width, fitted.height)
    }

    private fun loadForEmbedding(contentUri: String, size: Int): Bitmap =
        decodeFitted(contentUri, size)


    /**
     * Bitmap → tenseur CHW selon les conventions déclarées par le modèle :
     * `channel_order` (rgb/bgr) et `normalization` (mean_std/none). Sert donc le
     * détecteur (BGR brut 0-255) comme l'embedder (RGB normalisé) — les mean/std
     * du config.json sont donnés en RGB, on normalise avant de réordonner.
     */
    private fun imageTensor(loaded: Loaded, bitmap: Bitmap): OnnxTensor {
        val size = bitmap.width
        val area = size * size
        val pixels = loaded.pixelBuffer?.takeIf { it.size == area }
            ?: IntArray(area).also { loaded.pixelBuffer = it }
        val floats = loaded.floatBuffer?.takeIf { it.size == 3 * area }
            ?: FloatArray(3 * area).also { loaded.floatBuffer = it }

        val normalize = loaded.config.normalization == ModelConfig.NORM_MEAN_STD
        val bgr = loaded.config.channelOrder == ModelConfig.CHANNEL_BGR

        var m0 = 0f
        var m1 = 0f
        var m2 = 0f
        var s0 = 1f
        var s1 = 1f
        var s2 = 1f
        if (normalize) {
            val mean = loaded.config.mean ?: error("image.mean manquant")
            val std = loaded.config.std ?: error("image.std manquant")
            m0 = mean[0].toFloat(); m1 = mean[1].toFloat(); m2 = mean[2].toFloat()
            s0 = std[0].toFloat(); s1 = std[1].toFloat(); s2 = std[2].toFloat()
        }

        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        for (i in 0 until area) {
            val p = pixels[i]
            var r = ((p shr 16) and 0xFF).toFloat()
            var g = ((p shr 8) and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()

            if (normalize) {
                r = (r / 255f - m0) / s0
                g = (g / 255f - m1) / s1
                b = (b / 255f - m2) / s2
            }

            if (bgr) {
                val tmp = r
                r = b
                b = tmp
            }

            floats[i] = r
            floats[area + i] = g
            floats[2 * area + i] = b
        }
        return OnnxTensor.createTensor(
            env, FloatBuffer.wrap(floats), longArrayOf(1, 3, size.toLong(), size.toLong())
        )
    }

    /** Visage redressé sur le gabarit ArcFace, prêt pour l'embedder. */
    private fun alignFace(source: Bitmap, landmarks: FloatArray, outputSize: Int): Bitmap {
        val src = landmarks.toPixels(source.width, source.height)
        val similarity = similarityTransform(src, ARCFACE_TEMPLATE_112)
        val matrix = Matrix().apply {
            setValues(
                floatArrayOf(
                    similarity.a, -similarity.b, similarity.tx,
                    similarity.b, similarity.a, similarity.ty,
                    0f, 0f, 1f
                )
            )
        }

        val aligned = createBitmap(outputSize, outputSize)
        val paint = Paint().apply { isFilterBitmap = true }
        Canvas(aligned).drawBitmap(source, matrix, paint)
        return aligned
    }

    /**
     * Crop aligné → vecteur normalisé L2, dans l'espace de l'embedder.
     *
     * Ne prend pas le verrou et ne résout aucun modèle : [loaded] est l'embedder
     * déjà résolu par l'appelant, qui détient le verrou. Le `Mutex` n'étant pas
     * réentrant, le reprendre ici gèlerait la coroutine.
     *
     * Contrairement au détecteur, ce modèle n'a **qu'une** sortie `[1, dim]`
     * (vérifié à l'export) : ses nombres *sont* le résultat, il n'y a rien à
     * post-traiter.
     */
    private fun embedFace(loaded: Loaded, crop: Bitmap): FloatArray {
        val session = loaded.session
            ?: openSession(loaded, ModelConfig.FILE_FACE_EMBEDDER).also { loaded.session = it }

        val tensor = imageTensor(loaded, crop)
        try {
            val inputName = session.inputInfo.keys.first()
            return session.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val dim = output.info.shape.last().toInt()
                val expected = loaded.config.embeddingDim ?: error("embedding_dim manquant")
                require(dim == expected) { "sortie de dimension $dim, embedding_dim déclare $expected" }

                val vector = FloatArray(dim)
                output.floatBuffer.get(vector)
                vector.normalizeL2()
            }
        } finally {
            tensor.close()
        }
    }

    suspend fun analyzeFaces(contentUri: String): List<AnalyzedFace> {
        val faces = locateFaces(contentUri)
        if (faces.isEmpty()) return emptyList()

        return mutex.withLock {
            val loaded = ensureEmbedderLocked() ?: return emptyList()
            val size = loaded.config.inputSize ?: error("input_size manquant")

            val source = loadForEmbedding(contentUri, EMBED_SOURCE_SIZE)
            faces.map { face ->
                val crop = alignFace(source, face.landmarks, size)
                val embeding = embedFace(loaded, crop)
                AnalyzedFace(face, embeding)
            }
        }
    }

    private companion object {
        /** Rechargement pour les crops : taille fixe pour l'instant (cf. backlog). */
        const val EMBED_SOURCE_SIZE = 1024
    }
}
