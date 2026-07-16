package com.cevague.vindex.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les mesures de qualité ne lèvent jamais d'exception : une formule fausse produit un
 * nombre plausible, qui deviendrait un seuil faux. D'où des valeurs calculées à la
 * main et des cas dont on connaît la réponse par construction.
 */
class FaceQualityTest {

    // --------------------------------------------------------- résidu d'alignement

    /**
     * Le gabarit comparé à lui-même : la transformation est l'identité et le résidu
     * exactement nul. Si ce test tombe, tout le reste ment.
     */
    @Test
    fun `le gabarit sur lui-meme a un residu nul`() {
        val t = similarityTransform(ARCFACE_TEMPLATE_112, ARCFACE_TEMPLATE_112)

        assertEquals(0f, alignmentResidual(ARCFACE_TEMPLATE_112, ARCFACE_TEMPLATE_112, t), 0.001f)
        assertEquals(1f, t.scale(), 0.001f)
        assertEquals(0f, t.rollDegrees(), 0.001f)
    }

    /**
     * Une similarité **exacte** du gabarit (moitié, tournée, décalée) : il existe une
     * transformation qui la ramène pile dessus, donc résidu nul malgré des points très
     * différents. C'est le cœur de la mesure — elle ignore l'échelle et la rotation,
     * et ne voit que ce qu'aucune similarité ne peut corriger.
     */
    @Test
    fun `une vraie similarite du gabarit a un residu nul`() {
        // rotation 90° + demi-échelle + translation : (x,y) -> (-0.5y + 10, 0.5x + 20)
        val transformed = FloatArray(ARCFACE_TEMPLATE_112.size)
        for (i in 0 until 5) {
            val x = ARCFACE_TEMPLATE_112[2 * i]
            val y = ARCFACE_TEMPLATE_112[2 * i + 1]
            transformed[2 * i] = -0.5f * y + 10f
            transformed[2 * i + 1] = 0.5f * x + 20f
        }

        val t = similarityTransform(transformed, ARCFACE_TEMPLATE_112)

        assertEquals(0f, alignmentResidual(transformed, ARCFACE_TEMPLATE_112, t), 0.01f)
        // Le gabarit est 2× la source : la transformation doit ré-agrandir d'autant.
        assertEquals(2f, t.scale(), 0.01f)
    }

    /**
     * Le cas qui motive la mesure : un profil. Le nez déporté hors du triangle des
     * yeux est une déformation qu'**aucune** rotation ni échelle ne rattrape — le
     * résidu doit donc être franchement non nul.
     */
    @Test
    fun `un nez deporte laisse un residu`() {
        val profil = ARCFACE_TEMPLATE_112.copyOf()
        profil[4] -= 20f // nez décalé de 20 px vers la gauche

        val t = similarityTransform(profil, ARCFACE_TEMPLATE_112)

        assertTrue(
            "un profil doit laisser un résidu net, obtenu ${alignmentResidual(profil, ARCFACE_TEMPLATE_112, t)}",
            alignmentResidual(profil, ARCFACE_TEMPLATE_112, t) > 3f
        )
    }

    // ------------------------------------------------------------------ lacet

    /** Gabarit frontal : le nez est au milieu des yeux, lacet ~0. */
    @Test
    fun `le gabarit frontal a un lacet quasi nul`() {
        assertTrue(
            "attendu ~0, obtenu ${yawProxy(ARCFACE_TEMPLATE_112)}",
            kotlin.math.abs(yawProxy(ARCFACE_TEMPLATE_112)) < 0.05f
        )
    }

    /** Le lacet est **signé** : il dit de quel côté la tête tourne. */
    @Test
    fun `le lacet est signe`() {
        val gauche = ARCFACE_TEMPLATE_112.copyOf().also { it[4] -= 15f }
        val droite = ARCFACE_TEMPLATE_112.copyOf().also { it[4] += 15f }

        assertTrue(yawProxy(gauche) < -0.1f)
        assertTrue(yawProxy(droite) > 0.1f)
    }

    /**
     * Invariant sous rotation : une tête inclinée n'est pas une tête tournée. Le lacet
     * projette sur l'axe des yeux justement pour ne pas confondre les deux.
     */
    @Test
    fun `le lacet ignore l'inclinaison`() {
        val droit = floatArrayOf(0f, 0f, 10f, 0f, 5f, 5f, 2f, 10f, 8f, 10f)
        // même visage tourné de 90° : (x,y) -> (-y, x)
        val incline = FloatArray(10)
        for (i in 0 until 5) {
            incline[2 * i] = -droit[2 * i + 1]
            incline[2 * i + 1] = droit[2 * i]
        }

        assertEquals(yawProxy(droit), yawProxy(incline), 0.001f)
    }

    // ------------------------------------------------------------------ netteté

    /** Une image uniforme n'a aucun bord : variance du laplacien nulle. */
    @Test
    fun `une image plate n'a aucune nettete`() {
        assertEquals(0f, laplacianVariance(FloatArray(25) { 128f }, 5, 5), 0.001f)
    }

    /** Un damier est le maximum de bords : sa variance doit écraser celle d'un dégradé. */
    @Test
    fun `un damier est plus net qu'un degrade`() {
        val damier = FloatArray(64) { i -> if ((i / 8 + i % 8) % 2 == 0) 0f else 255f }
        val degrade = FloatArray(64) { i -> (i % 8) * 32f }

        assertTrue(laplacianVariance(damier, 8, 8) > laplacianVariance(degrade, 8, 8))
    }

    /** Trop petite pour avoir un intérieur : pas de valeur inventée. */
    @Test
    fun `une image minuscule renvoie zero`() {
        assertEquals(0f, laplacianVariance(FloatArray(4) { 100f }, 2, 2), 0.001f)
    }

    // ------------------------------------------------- exposition, cadrage, forme

    @Test
    fun `moyenne et ecart-type`() {
        val (mean, std) = meanStd(floatArrayOf(10f, 20f, 30f, 40f))

        assertEquals(25f, mean, 0.001f)
        assertEquals(11.180f, std, 0.01f) // sqrt(125)
    }

    /** Une boîte centrée a de la marge ; une boîte qui déborde la rend négative. */
    @Test
    fun `la marge au bord devient negative hors cadre`() {
        assertEquals(0.2f, edgeMargin(0.2f, 0.3f, 0.7f, 0.8f), 0.001f)
        assertTrue("boîte débordante", edgeMargin(-0.05f, 0.3f, 0.7f, 0.8f) < 0f)
    }

    @Test
    fun `le ratio de boite`() {
        assertEquals(0.5f, boxAspect(0f, 0f, 0.5f, 1f), 0.001f)
        assertEquals(0f, boxAspect(0f, 0.5f, 0.5f, 0.5f), 0.001f) // hauteur nulle
    }

    @Test
    fun `ecart inter-oculaire`() {
        assertEquals(10f, eyeDistance(floatArrayOf(0f, 0f, 10f, 0f)), 0.001f)
        assertEquals(5f, eyeDistance(floatArrayOf(0f, 0f, 3f, 4f)), 0.001f)
    }
}
