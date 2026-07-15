package com.cevague.vindex.ai

/**
 * Alignement d'un visage sur le gabarit canonique d'ArcFace : les modèles
 * d'embedding sont entraînés sur des crops 112×112 **alignés**, et sauter
 * l'étape dégrade en silence (clusters flous, seuils sans signification).
 *
 * Arithmétique pure : **aucune dépendance Android**, donc testable sur JVM
 * (`FaceAlignerTest`). L'application de la transformation à un bitmap vit dans
 * `FaceEngine`.
 */

/**
 * Gabarit ArcFace 5 points pour un crop 112×112 (x, y entrelacés).
 *
 * L'ordre est celui des **indices**, pas des noms : le point 0 est l'œil du
 * côté gauche de l'image (x = 38,3). Il coïncide index par index avec la sortie
 * de YuNet — vérifié en **mesurant**, les deux conventions de nommage se
 * contredisant (« right eye » chez YuNet désigne l'œil droit du *sujet*, donc
 * celui qui apparaît à gauche de l'image).
 */
val ARCFACE_TEMPLATE_112 = floatArrayOf(
    38.2946f, 51.6963f,   // œil, côté gauche de l'image
    73.5318f, 51.5014f,   // œil, côté droit
    56.0252f, 71.7366f,   // nez
    41.5493f, 92.3655f,   // bouche, côté gauche
    70.7299f, 92.2041f    // bouche, côté droit
)

/**
 * Similarité 2D : `x' = a·x − b·y + tx` et `y' = b·x + a·y + ty`.
 *
 * (a, b) portent rotation et échelle uniforme — c'est la multiplication par le
 * complexe `a + i·b` (angle `atan2(b, a)`, facteur `√(a² + b²)`). Cette
 * paramétrisation **interdit structurellement les reflets** : aucun visage ne
 * peut sortir miroité.
 */
data class Similarity(val a: Float, val b: Float, val tx: Float, val ty: Float)

/**
 * Similarité aux moindres carrés envoyant [src] sur [dst] (paires x, y
 * entrelacées, mêmes longueurs).
 *
 * Avec 5 points le système est **surdéterminé** (10 équations, 4 inconnues) :
 * un landmark aberrant est absorbé par les autres au lieu de dicter la
 * transformation — c'est l'intérêt d'utiliser les 5 points plutôt que les yeux
 * seuls.
 */
fun similarityTransform(src: FloatArray, dst: FloatArray): Similarity {
    require(src.size == dst.size && src.size >= 4 && src.size % 2 == 0) {
        "src et dst doivent être des paires (x, y) de même longueur"
    }
    val count = src.size / 2

    var srcMeanX = 0f
    var srcMeanY = 0f
    var dstMeanX = 0f
    var dstMeanY = 0f
    for (i in 0 until count) {
        srcMeanX += src[2 * i]
        srcMeanY += src[2 * i + 1]
        dstMeanX += dst[2 * i]
        dstMeanY += dst[2 * i + 1]
    }
    srcMeanX /= count
    srcMeanY /= count
    dstMeanX /= count
    dstMeanY /= count

    // Nuages centrés : la translation est réglée (l'optimum envoie toujours un
    // centroïde sur l'autre), il ne reste que rotation et échelle.
    var energy = 0f
    var inPhase = 0f
    var quadrature = 0f
    for (i in 0 until count) {
        val x = src[2 * i] - srcMeanX
        val y = src[2 * i + 1] - srcMeanY
        val targetX = dst[2 * i] - dstMeanX
        val targetY = dst[2 * i + 1] - dstMeanY

        energy += x * x + y * y
        inPhase += x * targetX + y * targetY
        quadrature += x * targetY - y * targetX
    }
    require(energy > 0f) { "points source dégénérés (tous confondus)" }

    val a = inPhase / energy
    val b = quadrature / energy
    return Similarity(
        a = a,
        b = b,
        tx = dstMeanX - (a * srcMeanX - b * srcMeanY),
        ty = dstMeanY - (b * srcMeanX + a * srcMeanY)
    )
}

/** Points 0-1 entrelacés → pixels d'une image [width]×[height]. */
fun FloatArray.toPixels(width: Int, height: Int): FloatArray {
    val pixels = FloatArray(size)
    for (i in indices step 2) {
        pixels[i] = this[i] * width
        pixels[i + 1] = this[i + 1] * height
    }
    return pixels
}

