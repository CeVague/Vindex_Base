package com.cevague.vindex.ai

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Post-traitement d'un détecteur de visages type YuNet : décodage des sorties
 * brutes, NMS, filtres, normalisation. Reproduit le post-traitement de référence
 * d'OpenCV (`FaceDetectorYNImpl`, modules/objdetect/src/face_detect.cpp).
 *
 * Arithmétique pure : **aucune dépendance Android ni ONNX**, donc testable sur
 * JVM (`FaceDecoderTest`) — c'est délibéré, un décodage faux donne des visages
 * faux en silence.
 */

/** Boîte candidate, en pixels du canevas letterboxé, avant NMS. */
data class Candidate(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val score: Float,
    /** 5 points (x, y) entrelacés, même repère que la boîte. */
    val landmarks: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Candidate
        return x1 == other.x1 && y1 == other.y1 && x2 == other.x2 && y2 == other.y2 &&
                score == other.score && landmarks.contentEquals(other.landmarks)
    }

    override fun hashCode(): Int {
        var result = x1.hashCode()
        result = 31 * result + y1.hashCode()
        result = 31 * result + x2.hashCode()
        result = 31 * result + y2.hashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + landmarks.contentHashCode()
        return result
    }
}

/** Visage retenu : boîte et landmarks normalisés 0-1 sur la photo d'origine. */
data class DetectedFace(
    val boxLeft: Float, val boxTop: Float, val boxRight: Float, val boxBottom: Float,
    /** 5 points (x, y) entrelacés, normalisés 0-1, **non bornés** (cf. [toDetectedFaces]). */
    val landmarks: FloatArray,
    val score: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DetectedFace
        return boxLeft == other.boxLeft && boxTop == other.boxTop &&
                boxRight == other.boxRight && boxBottom == other.boxBottom &&
                score == other.score && landmarks.contentEquals(other.landmarks)
    }

    override fun hashCode(): Int {
        var result = boxLeft.hashCode()
        result = 31 * result + boxTop.hashCode()
        result = 31 * result + boxRight.hashCode()
        result = 31 * result + boxBottom.hashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + landmarks.contentHashCode()
        return result
    }
}

/**
 * Taille de décodage de la photo pour tailler les crops d'embedding.
 *
 * Charger à une taille fixe fait payer le plus petit visage : à 1024 px, un
 * visage occupant 4 % de la largeur ne fait que 41 pixels, et l'alignement
 * l'**agrandit** vers 112×112 en inventant les pixels manquants. L'embedding qui
 * en sort n'est pas faux, il est flou — et un vecteur flou n'échoue jamais
 * bruyamment : il s'éloigne un peu de tout, et la personne se scinde en deux
 * groupes sans que rien ne le signale.
 *
 * La règle : le plus petit visage doit valoir au moins [cropSize] pixels.
 * [baseWidth] est la largeur **réellement décodée** à [baseSize] et non [baseSize]
 * lui-même — un « fit » borne la plus grande dimension, donc un portrait décodé
 * dans un carré de 1024 est plus étroit que 1024, et raisonner sur la taille
 * demandée sous-provisionnerait tous les portraits.
 *
 * Renvoie [baseSize] quand le plus petit visage y est déjà assez grand (le cas
 * courant : aucune relecture), sinon la taille agrandie, plafonnée par [maxSize]
 * — au-delà, le coût mémoire d'un bitmap ne se justifie plus pour un visage que
 * la détection a de toute façon vu de très loin.
 */
fun embedSourceSize(
    smallestBoxWidth: Float,
    baseSize: Int,
    baseWidth: Int,
    cropSize: Int,
    maxSize: Int
): Int {
    val facePixels = smallestBoxWidth * baseWidth
    if (facePixels <= 0f) return baseSize
    val factor = cropSize / facePixels
    if (factor <= 1f) return baseSize
    return (baseSize * factor).toInt().coerceIn(baseSize, maxSize)
}

/**
 * Décode un niveau de la pyramide. Le prior d'index `a` est la case
 * `(a / cols, a % cols)` de la grille ; le réseau ne prédit que des écarts à
 * cette case. Le score combine classification et objectness (moyenne
 * géométrique, les deux étant déjà des probabilités), et la **taille** est
 * encodée en logarithme — d'où l'`exp`, sur w/h uniquement.
 */
fun decodeStride(
    cls: FloatArray,
    obj: FloatArray,
    bbox: FloatArray,
    kps: FloatArray,
    stride: Int,
    cols: Int,
    scoreThreshold: Float
): List<Candidate> {
    val candidates = mutableListOf<Candidate>()

    for (a in cls.indices) {
        val score = sqrt(cls[a].coerceIn(0f, 1f) * obj[a].coerceIn(0f, 1f))
        if (score < scoreThreshold) continue

        val r = a / cols
        val c = a % cols

        val cx = (c + bbox[a * 4]) * stride
        val cy = (r + bbox[a * 4 + 1]) * stride
        val w = exp(bbox[a * 4 + 2]) * stride
        val h = exp(bbox[a * 4 + 3]) * stride

        val landmarks = FloatArray(10)
        for (n in 0 until 5) {
            landmarks[2 * n] = (kps[a * 10 + 2 * n] + c) * stride
            landmarks[2 * n + 1] = (kps[a * 10 + 2 * n + 1] + r) * stride
        }

        val x1 = cx - w / 2
        val y1 = cy - h / 2
        candidates.add(Candidate(x1, y1, x1 + w, y1 + h, score, landmarks))
    }

    return candidates
}

/** Recouvrement de deux boîtes : 0 (disjointes) à 1 (identiques). */
fun iou(a: Candidate, b: Candidate): Float {
    val interWidth = max(0f, min(a.x2, b.x2) - max(a.x1, b.x1))
    val interHeight = max(0f, min(a.y2, b.y2) - max(a.y1, b.y1))
    val intersection = interWidth * interHeight

    val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
    val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
    val union = areaA + areaB - intersection

    return if (union <= 0f) 0f else intersection / union
}

/**
 * Non-Maximum Suppression : ne garde qu'une boîte par groupe qui se recouvre.
 * Le tri par score décroissant est ce qui rend la décision **locale** (« est-ce
 * que je recouvre une boîte déjà retenue ? ») équivalente à la décision globale
 * (« suis-je le meilleur de mon groupe ? ») — sans jamais former de groupes.
 */
fun nms(candidates: List<Candidate>, iouThreshold: Float): List<Candidate> {
    val kept = mutableListOf<Candidate>()
    for (candidate in candidates.sortedByDescending { it.score }) {
        if (kept.none { iou(candidate, it) > iouThreshold }) kept.add(candidate)
    }
    return kept
}

/**
 * Écarte les visages dont un côté est sous [minFaceSize] (pixels du canevas) :
 * les micro-visages (avatars de captures d'écran, visages dans un article)
 * produisent des embeddings-bruit qui polluent les clusters.
 */
fun List<Candidate>.filterMinSize(minFaceSize: Int?): List<Candidate> {
    if (minFaceSize == null) return this
    return filter { it.x2 - it.x1 >= minFaceSize && it.y2 - it.y1 >= minFaceSize }
}

/**
 * Canevas → 0-1, en divisant par les dimensions du **contenu** (le letterbox
 * colle l'image en haut-gauche, donc aucun offset à défaire).
 *
 * La boîte est bornée à [0, 1] : un visage au bord déborde, et le stockage
 * comme `FaceCropTransformation` attendent du 0-1. Les **landmarks ne le sont
 * pas** : ils alimentent une transformation géométrique (l'alignement), et
 * borner un œil de 1,02 à 1,00 déplacerait le point → matrice faussée.
 */
fun List<Candidate>.toDetectedFaces(contentWidth: Int, contentHeight: Int): List<DetectedFace> =
    map { candidate ->
        val landmarks = FloatArray(candidate.landmarks.size)
        for (n in landmarks.indices step 2) {
            landmarks[n] = candidate.landmarks[n] / contentWidth
            landmarks[n + 1] = candidate.landmarks[n + 1] / contentHeight
        }
        DetectedFace(
            boxLeft = (candidate.x1 / contentWidth).coerceIn(0f, 1f),
            boxTop = (candidate.y1 / contentHeight).coerceIn(0f, 1f),
            boxRight = (candidate.x2 / contentWidth).coerceIn(0f, 1f),
            boxBottom = (candidate.y2 / contentHeight).coerceIn(0f, 1f),
            landmarks = landmarks,
            score = candidate.score
        )
    }
