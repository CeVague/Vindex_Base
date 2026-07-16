package com.cevague.vindex.ai

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Mesures de **qualité d'un visage détecté** : pose, netteté, exposition, cadrage.
 *
 * Pourquoi elles existent : la similarité seule ne suffit pas. Mesuré sur vérité
 * terrain (2026-07-15), **19 %** des paires d'une même personne tombent sous le
 * plafond des inconnus — et l'inspection à l'œil a montré que ce ne sont PAS des
 * erreurs d'étiquetage : ce sont des visages de profil extrême, tronqués au bord du
 * cadre, ou que le détecteur voit mal. Aucun seuil ne les rattrape ; il faut donc
 * savoir *reconnaître* qu'un visage est mauvais, indépendamment de qui il est.
 *
 * Arithmétique pure, **aucune dépendance Android** : une métrique fausse ne lève
 * jamais d'exception, elle biaise en silence. D'où les tests.
 */

/** Facteur d'échelle de la transformation (≫1 = visage agrandi, donc peu de pixels). */
fun Similarity.scale(): Float = hypot(a, b)

/** Rotation dans le plan (roll), en degrés. */
fun Similarity.rollDegrees(): Float = Math.toDegrees(atan2(b, a).toDouble()).toFloat()

/** Applique la similarité à un point : c'est la matrice de `alignFace`. */
private fun Similarity.apply(x: Float, y: Float): Pair<Float, Float> =
    (a * x - b * y + tx) to (b * x + a * y + ty)

/**
 * **Résidu d'alignement** : distance RMS entre les 5 ancres projetées et le gabarit,
 * en pixels du gabarit (112).
 *
 * C'est le capteur de pose le plus honnête dont on dispose, et il est gratuit :
 * `similarityTransform` ajuste 4 paramètres sur 10 contraintes (5 points × 2), donc
 * le système est **surdéterminé** et son résidu dit exactement ce qu'on veut savoir —
 * à quel point la géométrie du visage **refuse** de se ramener à un visage de face.
 *
 * Un visage frontal net : les 5 points tombent presque sur le gabarit, résidu ~0.
 * Un profil : aucune rotation+échelle ne peut y superposer le gabarit (le nez sort du
 * triangle des yeux), résidu élevé. C'est de la géométrie, pas de l'apprentissage.
 */
fun alignmentResidual(src: FloatArray, dst: FloatArray, t: Similarity): Float {
    require(src.size == dst.size && src.size % 2 == 0) { "ancres incohérentes" }
    var sumSq = 0.0
    val n = src.size / 2
    for (i in 0 until n) {
        val (px, py) = t.apply(src[2 * i], src[2 * i + 1])
        val dx = px - dst[2 * i]
        val dy = py - dst[2 * i + 1]
        sumSq += dx.toDouble() * dx + dy.toDouble() * dy
    }
    return sqrt(sumSq / n).toFloat()
}

/**
 * **Lacet (yaw)** approché : décalage du nez par rapport au milieu des yeux, rapporté
 * à l'écart des yeux. 0 = de face, ±0,5 et plus = profil marqué.
 *
 * Redondant avec [alignmentResidual] ? Non : le résidu dit « cette géométrie est
 * anormale » sans dire pourquoi (profil ? ancre ratée ? grimace ?), le lacet est
 * **signé** et ne parle que du profil. Les deux ensemble séparent « tourné » de
 * « bizarre ».
 *
 * Ancres YuNet : 0-1 = yeux, 2 = nez, 3-4 = coins de la bouche.
 */
fun yawProxy(landmarks: FloatArray): Float {
    require(landmarks.size >= 6) { "5 ancres attendues" }
    val eyeMidX = (landmarks[0] + landmarks[2]) / 2f
    val eyeMidY = (landmarks[1] + landmarks[3]) / 2f
    val eyeDist = hypot(landmarks[2] - landmarks[0], landmarks[3] - landmarks[1])
    if (eyeDist <= 0f) return 0f
    // Projeté sur l'axe des yeux : reste juste quand la tête est aussi inclinée.
    val axisX = (landmarks[2] - landmarks[0]) / eyeDist
    val axisY = (landmarks[3] - landmarks[1]) / eyeDist
    val noseDx = landmarks[4] - eyeMidX
    val noseDy = landmarks[5] - eyeMidY
    return (noseDx * axisX + noseDy * axisY) / eyeDist
}

/**
 * **Tangage (pitch)** approché : décalage du nez **perpendiculairement** à l'axe des
 * yeux, rapporté à l'écart des yeux, moins sa valeur au repos.
 *
 * [yawProxy] projette le nez *sur* l'axe des yeux et ne voit donc que la rotation
 * horizontale — un visage renversé en arrière lui échappe complètement (mesuré :
 * |yaw| = 0,23 sur une grimace tête en arrière, contre 3,58 sur une tête tournée).
 * La composante perpendiculaire est la moitié manquante.
 *
 * Signé : négatif = menton relevé (nez remonte vers les yeux), positif = tête
 * penchée en avant. Le zéro est calé sur le gabarit ArcFace, donc sur un visage de
 * face, et non sur une valeur arbitraire.
 */
fun pitchProxy(landmarks: FloatArray): Float {
    require(landmarks.size >= 6) { "5 ancres attendues" }
    val eyeDist = eyeDistance(landmarks)
    if (eyeDist <= 0f) return 0f
    val eyeMidX = (landmarks[0] + landmarks[2]) / 2f
    val eyeMidY = (landmarks[1] + landmarks[3]) / 2f
    // Normale à l'axe des yeux (rotation de 90°) : insensible à l'inclinaison, comme
    // le lacet — les deux se partagent le plan sans se recouvrir.
    val normalX = -(landmarks[3] - landmarks[1]) / eyeDist
    val normalY = (landmarks[2] - landmarks[0]) / eyeDist
    val projected =
        ((landmarks[4] - eyeMidX) * normalX + (landmarks[5] - eyeMidY) * normalY) / eyeDist
    return projected - TEMPLATE_PITCH
}

/** Valeur de [pitchProxy] sur le gabarit : le zéro d'un visage de face. */
private val TEMPLATE_PITCH: Float = run {
    val l = ARCFACE_TEMPLATE_112
    val eyeDist = eyeDistance(l)
    val eyeMidX = (l[0] + l[2]) / 2f
    val eyeMidY = (l[1] + l[3]) / 2f
    val nx = -(l[3] - l[1]) / eyeDist
    val ny = (l[2] - l[0]) / eyeDist
    ((l[4] - eyeMidX) * nx + (l[5] - eyeMidY) * ny) / eyeDist
}

/**
 * **Cette détection mérite-t-elle d'exister ?** 0 (rebut) à 1 (bon visage).
 *
 * ⚠ Ne répond **pas** à « ce visage est-il facile à reconnaître ? » — ce sont deux
 * questions distinctes, et **leurs métriques ne se recouvrent pas** (mesuré le
 * 2026-07-16 sur 102 visages étiquetés à la main) :
 *  - garder ou jeter : `align_scale` (AUC 0,90), `blur` (0,86), `det_score` (0,86) ;
 *    `|yaw|` et `align_rmse` y sont **inutiles** (0,59 / 0,54).
 *  - difficile ou non : `|yaw|` (−0,58), `det_score` (+0,56), `align_rmse` (−0,43) ;
 *    `blur` et la taille y sont **inutiles** (−0,09 / +0,01).
 * Seul `det_score` sert aux deux, d'où sa présence ici.
 *
 * Produit et non moyenne : les trois signaux doivent être **tous** corrects. Une main
 * bien nette et bien détectée reste une main — c'est `align_scale` qui la trahit.
 *
 * ⚠ **Ne détecte pas les dessins** : mesuré, 0/9 rejetés. Un dessin est frontal, net
 * et bien détecté — géométriquement *plus* parfait qu'un vrai visage (son résidu
 * d'alignement est meilleur). C'est un problème sémantique, pas de qualité ; il reste
 * à l'utilisateur.
 */
fun faceQuality(detScore: Float, alignScale: Float, blur: Float): Float {
    // Agrandissement pour tenir le gabarit : > 1 = le visage avait moins de 112 px,
    // on a inventé les pixels manquants. Le signal le plus fort (AUC 0,90).
    val scale = (1f / alignScale.coerceAtLeast(1e-3f)).coerceAtMost(2f) / 2f
    // En ordres de grandeur : le flou s'étale sur 3 décades (rebut ~3, visage ~400),
    // une échelle linéaire y écraserait tout.
    val sharp = ((log10(blur.coerceAtLeast(0.01f)) - 0.5f) / 2f).coerceIn(0f, 1f)
    return detScore.coerceIn(0f, 1f) * scale * sharp
}

/** Écart inter-oculaire, dans l'unité des ancres fournies. */
fun eyeDistance(landmarks: FloatArray): Float {
    require(landmarks.size >= 4) { "2 ancres d'yeux attendues" }
    return hypot(landmarks[2] - landmarks[0], landmarks[3] - landmarks[1])
}

/**
 * **Netteté** : variance du laplacien, la mesure de flou classique.
 *
 * Le laplacien répond aux changements brusques d'intensité ; une image nette en a
 * beaucoup (donc forte variance), une image floue les a lissés (variance basse).
 * Calculée sur le crop **aligné**, donc à cadrage constant — deux visages sont
 * comparables même si l'un était plus loin.
 *
 * ⚠ Dépend du contenu autant que du flou (un visage lisse sur fond uni score bas
 * sans être flou). À prendre comme indice, pas comme vérité — d'où la mesure sur
 * données réelles avant d'en faire un seuil.
 */
fun laplacianVariance(gray: FloatArray, width: Int, height: Int): Float {
    require(gray.size == width * height) { "dimensions incohérentes" }
    if (width < 3 || height < 3) return 0f
    var sum = 0.0
    var sumSq = 0.0
    var n = 0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val i = y * width + x
            val lap = 4f * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - width] - gray[i + width]
            sum += lap
            sumSq += lap.toDouble() * lap
            n++
        }
    }
    if (n == 0) return 0f
    val mean = sum / n
    return (sumSq / n - mean * mean).toFloat()
}

/** Moyenne et écart-type des niveaux de gris : exposition et contraste du crop. */
fun meanStd(gray: FloatArray): Pair<Float, Float> {
    if (gray.isEmpty()) return 0f to 0f
    var sum = 0.0
    var sumSq = 0.0
    for (v in gray) {
        sum += v
        sumSq += v.toDouble() * v
    }
    val mean = sum / gray.size
    val variance = (sumSq / gray.size - mean * mean).coerceAtLeast(0.0)
    return mean.toFloat() to sqrt(variance).toFloat()
}

/**
 * Marge minimale entre la boîte et le bord de l'image, normalisée 0-1 ; **négative**
 * quand la boîte déborde.
 *
 * Cas réel qui motive la mesure : un visage collé au bord du cadre est tronqué, donc
 * amputé de la moitié de son information — mais rien dans son score de détection ne
 * le dit.
 */
fun edgeMargin(left: Float, top: Float, right: Float, bottom: Float): Float =
    minOf(left, top, 1f - right, 1f - bottom)

/** Boîte plus haute que large (ou l'inverse) : un profil se resserre. */
fun boxAspect(left: Float, top: Float, right: Float, bottom: Float): Float {
    val w = abs(right - left)
    val h = abs(bottom - top)
    return if (h <= 0f) 0f else w / h
}
