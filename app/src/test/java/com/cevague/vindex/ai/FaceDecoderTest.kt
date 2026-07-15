package com.cevague.vindex.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

/**
 * Le décodage YuNet est le maillon où une erreur ne se voit pas : un indice
 * inversé ou un `exp` oublié donne des boîtes plausibles mais fausses, donc des
 * embeddings faux, donc des clusters flous — sans la moindre exception. D'où ce
 * filet : chaque formule est figée sur des valeurs calculées à la main.
 */
class FaceDecoderTest {

    private fun box(x1: Float, y1: Float, x2: Float, y2: Float, score: Float = 1f) =
        Candidate(x1, y1, x2, y2, score, FloatArray(10))

    // ------------------------------------------------------------- decodeStride

    /**
     * Grille 2×2 au stride 8 : seul le prior 3 a un score, il vit donc en
     * `(r=1, c=1)`. Deltas centre 0,5 → centre (12, 12) ; deltas taille 0 →
     * `exp(0) * 8 = 8` → boîte (8, 8) → (16, 16).
     */
    @Test
    fun `decodeStride decode le prior attendu`() {
        val cls = floatArrayOf(0f, 0f, 0f, 1f)
        val obj = floatArrayOf(0f, 0f, 0f, 1f)
        val bbox = FloatArray(16).apply {
            this[12] = 0.5f; this[13] = 0.5f; this[14] = 0f; this[15] = 0f
        }
        val kps = FloatArray(40).apply {
            this[30] = 0.5f; this[31] = -0.5f
        }

        val result = decodeStride(cls, obj, bbox, kps, stride = 8, cols = 2, scoreThreshold = 0.5f)

        assertEquals(1, result.size)
        val c = result[0]
        assertEquals(8f, c.x1, 0.001f)
        assertEquals(8f, c.y1, 0.001f)
        assertEquals(16f, c.x2, 0.001f)
        assertEquals(16f, c.y2, 0.001f)
        assertEquals(1f, c.score, 0.001f)
        // lm0 : (0,5 + c=1) * 8 = 12 et (-0,5 + r=1) * 8 = 4 ; les autres (0 + 1) * 8 = 8.
        assertArrayEquals(
            floatArrayOf(12f, 4f, 8f, 8f, 8f, 8f, 8f, 8f, 8f, 8f),
            c.landmarks,
            0.001f
        )
    }

    /** La taille est encodée en logarithme : un delta de ln(2) doit doubler le côté. */
    @Test
    fun `decodeStride applique exp sur la taille`() {
        val bbox = floatArrayOf(0f, 0f, ln(2f), ln(2f))
        val result = decodeStride(
            cls = floatArrayOf(1f), obj = floatArrayOf(1f), bbox = bbox, kps = FloatArray(10),
            stride = 8, cols = 1, scoreThreshold = 0.5f
        )

        assertEquals(16f, result[0].x2 - result[0].x1, 0.001f)
        assertEquals(16f, result[0].y2 - result[0].y1, 0.001f)
    }

    /** Le score est la moyenne géométrique : sqrt(0,5 × 0,5) = 0,5. */
    @Test
    fun `decodeStride ecarte les scores sous le seuil`() {
        val cls = floatArrayOf(0.5f)
        val obj = floatArrayOf(0.5f)
        val bbox = FloatArray(4)
        val kps = FloatArray(10)

        val rejected = decodeStride(cls, obj, bbox, kps, 8, 1, scoreThreshold = 0.6f)
        val accepted = decodeStride(cls, obj, bbox, kps, 8, 1, scoreThreshold = 0.4f)

        assertEquals(0, rejected.size)
        assertEquals(1, accepted.size)
        assertEquals(0.5f, accepted[0].score, 0.001f)
    }

    // ---------------------------------------------------------------------- iou

    @Test
    fun `iou vaut 1 pour deux boites identiques`() {
        assertEquals(1f, iou(box(0f, 0f, 10f, 10f), box(0f, 0f, 10f, 10f)), 0.001f)
    }

    /** Le piège : sans bornage à 0, l'intersection « négative » donnerait un IoU délirant. */
    @Test
    fun `iou vaut 0 pour deux boites disjointes`() {
        assertEquals(0f, iou(box(0f, 0f, 10f, 10f), box(20f, 20f, 30f, 30f)), 0.001f)
    }

    /** Intersection 5×5 = 25, union 100 + 100 − 25 = 175. */
    @Test
    fun `iou d'un recouvrement partiel`() {
        assertEquals(25f / 175f, iou(box(0f, 0f, 10f, 10f), box(5f, 5f, 15f, 15f)), 0.001f)
    }

    /** Deux boîtes d'aire nulle : union nulle → 0, surtout pas NaN. */
    @Test
    fun `iou vaut 0 quand l'union est nulle`() {
        assertEquals(0f, iou(box(0f, 0f, 0f, 0f), box(0f, 0f, 0f, 0f)), 0.001f)
    }

    // ---------------------------------------------------------------------- nms

    @Test
    fun `nms supprime le doublon et garde la boite disjointe`() {
        val best = box(0f, 0f, 10f, 10f, score = 0.9f)
        val duplicate = box(1f, 1f, 11f, 11f, score = 0.8f)
        val other = box(100f, 100f, 110f, 110f, score = 0.7f)

        val kept = nms(listOf(best, duplicate, other), iouThreshold = 0.3f)

        assertEquals(2, kept.size)
        assertSame(best, kept[0])
        assertSame(other, kept[1])
    }

    /** Le tri est ce qui garantit que le meilleur survit, quel que soit l'ordre d'entrée. */
    @Test
    fun `nms garde le mieux note quel que soit l'ordre`() {
        val weak = box(0f, 0f, 10f, 10f, score = 0.4f)
        val strong = box(1f, 1f, 11f, 11f, score = 0.95f)

        val kept = nms(listOf(weak, strong), iouThreshold = 0.3f)

        assertEquals(1, kept.size)
        assertSame(strong, kept[0])
    }

    @Test
    fun `nms sur une liste vide`() {
        assertEquals(0, nms(emptyList(), 0.3f).size)
    }

    // ------------------------------------------------------------- filterMinSize

    @Test
    fun `filterMinSize sans seuil ne filtre rien`() {
        val candidates = listOf(box(0f, 0f, 2f, 2f))
        assertEquals(candidates, candidates.filterMinSize(null))
    }

    /**
     * Le filtre porte sur **chaque côté**, pas sur l'aire : cette boîte de
     * 100×5 a une aire large (500) mais ne fait que 5 px de haut — c'est un
     * artefact, pas un visage.
     */
    @Test
    fun `filterMinSize ecarte une boite plate malgre son aire`() {
        assertEquals(0, listOf(box(0f, 0f, 100f, 5f)).filterMinSize(20).size)
    }

    @Test
    fun `filterMinSize garde une boite au dessus du seuil`() {
        assertEquals(1, listOf(box(0f, 0f, 30f, 30f)).filterMinSize(20).size)
        assertEquals(1, listOf(box(0f, 0f, 20f, 20f)).filterMinSize(20).size)
        assertEquals(0, listOf(box(0f, 0f, 19f, 19f)).filterMinSize(20).size)
    }

    // ---------------------------------------------------------- toDetectedFaces

    /** Contenu 640×480 : on divise x par 640 et y par 480, pas par la taille du canevas. */
    @Test
    fun `toDetectedFaces normalise par les dimensions du contenu`() {
        val candidate = Candidate(
            64f, 48f, 128f, 96f, 0.9f,
            floatArrayOf(64f, 48f, 320f, 240f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

        val face = listOf(candidate).toDetectedFaces(contentWidth = 640, contentHeight = 480)[0]

        assertEquals(0.1f, face.boxLeft, 0.001f)
        assertEquals(0.1f, face.boxTop, 0.001f)
        assertEquals(0.2f, face.boxRight, 0.001f)
        assertEquals(0.2f, face.boxBottom, 0.001f)
        assertEquals(0.9f, face.score, 0.001f)
        assertArrayEquals(
            floatArrayOf(0.1f, 0.1f, 0.5f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f),
            face.landmarks,
            0.001f
        )
    }

    /**
     * Un visage au bord déborde du contenu. La boîte est bornée (le stockage et
     * `FaceCropTransformation` attendent du 0-1) mais **pas les landmarks** :
     * borner un œil le déplacerait, et fausserait la matrice d'alignement.
     */
    @Test
    fun `toDetectedFaces borne la boite mais pas les landmarks`() {
        val candidate = Candidate(
            -64f, -48f, 1280f, 960f, 0.9f,
            floatArrayOf(-64f, -48f, 1280f, 960f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

        val face = listOf(candidate).toDetectedFaces(contentWidth = 640, contentHeight = 480)[0]

        assertEquals(0f, face.boxLeft, 0.001f)
        assertEquals(0f, face.boxTop, 0.001f)
        assertEquals(1f, face.boxRight, 0.001f)
        assertEquals(1f, face.boxBottom, 0.001f)
        assertArrayEquals(
            floatArrayOf(-0.1f, -0.1f, 2f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            face.landmarks,
            0.001f
        )
    }

    // ---------------------------------------------------------- embedSourceSize

    private fun sourceSize(smallestBoxWidth: Float, baseWidth: Int = 1024) = embedSourceSize(
        smallestBoxWidth = smallestBoxWidth,
        baseSize = 1024,
        baseWidth = baseWidth,
        cropSize = 112,
        maxSize = 3072
    )

    /** Un visage déjà plus grand que le crop : relire serait du gaspillage. */
    @Test
    fun `un grand visage ne fait pas relire la photo`() {
        // 0.5 × 1024 = 512 pixels, très au-dessus de 112.
        assertEquals(1024, sourceSize(0.5f))
    }

    /** Pile à la taille du crop : suffisant, donc on garde la base. */
    @Test
    fun `la frontiere est le crop lui-meme`() {
        // 0.109375 × 1024 = 112 pixels exactement.
        assertEquals(1024, sourceSize(0.109375f))
    }

    /**
     * Le cas qui motive tout : un petit visage n'atteint pas 112 px à la base, et
     * la taille relue est exactement celle qui l'y amène.
     */
    @Test
    fun `un petit visage fait relire juste assez grand`() {
        // 0.05 × 1024 = 51.2 px ; il faut 112/51.2 = 2.1875× → 2240.
        assertEquals(2240, sourceSize(0.05f))
        // À 2240, le visage vaut 0.05 × 2240 = 112 px.
    }

    /** Sous le plafond, un visage minuscule ne fait pas exploser la mémoire. */
    @Test
    fun `le plafond borne les visages minuscules`() {
        assertEquals(3072, sourceSize(0.001f))
    }

    /**
     * Le point à ne pas rater : la boîte est normalisée par rapport à la photo,
     * alors qu'un « fit » ne borne que la plus grande dimension. Un portrait décodé
     * dans un carré de 1024 est plus étroit que 1024, et son visage compte donc
     * moins de pixels à boîte égale — d'où le calcul sur la largeur réelle.
     */
    @Test
    fun `un portrait etroit demande plus qu'un paysage a boite egale`() {
        val paysage = sourceSize(0.08f, baseWidth = 1024)
        val portrait = sourceSize(0.08f, baseWidth = 768) // 3:4

        assertTrue("le portrait doit demander plus : $portrait vs $paysage", portrait > paysage)
    }

    /** Boîte dégénérée : on ne divise pas par zéro, on garde la base. */
    @Test
    fun `une boite de largeur nulle garde la base`() {
        assertEquals(1024, sourceSize(0f))
    }
}
