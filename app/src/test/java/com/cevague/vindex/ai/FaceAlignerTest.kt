package com.cevague.vindex.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * L'alignement est la seconde variable silencieuse de la chaîne visages (après
 * la quantification) : un crop mal aligné nourrit un embedder parfait avec des
 * pixels décalés, et sort des vecteurs faux sans la moindre erreur. Chaque
 * transformation élémentaire est donc figée ici.
 */
class FaceAlignerTest {

    /** Carré + centre : 5 points non alignés, comme un vrai jeu de landmarks. */
    private val points = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f, 5f, 5f)

    private fun apply(t: Similarity, source: FloatArray): FloatArray {
        val out = FloatArray(source.size)
        for (i in source.indices step 2) {
            out[i] = t.a * source[i] - t.b * source[i + 1] + t.tx
            out[i + 1] = t.b * source[i] + t.a * source[i + 1] + t.ty
        }
        return out
    }

    @Test
    fun `identite`() {
        val t = similarityTransform(points, points)

        assertEquals(1f, t.a, 0.001f)
        assertEquals(0f, t.b, 0.001f)
        assertEquals(0f, t.tx, 0.001f)
        assertEquals(0f, t.ty, 0.001f)
    }

    @Test
    fun `translation pure`() {
        val moved = apply(Similarity(1f, 0f, 10f, 5f), points)

        val t = similarityTransform(points, moved)

        assertEquals(1f, t.a, 0.001f)
        assertEquals(0f, t.b, 0.001f)
        assertEquals(10f, t.tx, 0.001f)
        assertEquals(5f, t.ty, 0.001f)
    }

    @Test
    fun `echelle pure`() {
        val scaled = apply(Similarity(2f, 0f, 0f, 0f), points)

        val t = similarityTransform(points, scaled)

        assertEquals(2f, t.a, 0.001f)
        assertEquals(0f, t.b, 0.001f)
    }

    /** Rotation de 90° : a = cos 90 = 0, b = sin 90 = 1. */
    @Test
    fun `rotation de 90 degres`() {
        val rotated = apply(Similarity(0f, 1f, 0f, 0f), points)

        val t = similarityTransform(points, rotated)

        assertEquals(0f, t.a, 0.001f)
        assertEquals(1f, t.b, 0.001f)
        assertEquals(0f, t.tx, 0.001f)
        assertEquals(0f, t.ty, 0.001f)
    }

    /** Le gabarit réel, déplacé et agrandi ×2 : on doit retrouver l'inverse exact. */
    @Test
    fun `recupere la transformation vers le gabarit ArcFace`() {
        val detected = apply(Similarity(2f, 0f, 100f, 50f), ARCFACE_TEMPLATE_112)

        val t = similarityTransform(detected, ARCFACE_TEMPLATE_112)

        assertEquals(0.5f, t.a, 0.001f)
        assertEquals(0f, t.b, 0.001f)
        assertEquals(-50f, t.tx, 0.001f)
        assertEquals(-25f, t.ty, 0.001f)
    }

    /**
     * La raison d'être des 5 points : le système est surdéterminé, donc un œil
     * mal détecté est **absorbé** par les quatre autres. Avec un calage sur les
     * deux yeux seuls (système exactement déterminé), ce même écart dicterait
     * toute la transformation.
     */
    @Test
    fun `un landmark aberrant ne deplace la transformation qu'a peine`() {
        val detected = apply(Similarity(2f, 0f, 100f, 50f), ARCFACE_TEMPLATE_112)
        val clean = similarityTransform(detected, ARCFACE_TEMPLATE_112)

        val perturbed = detected.copyOf().also { it[0] += 6f }
        val noisy = similarityTransform(perturbed, ARCFACE_TEMPLATE_112)

        assertTrue("a a bougé de ${abs(noisy.a - clean.a)}", abs(noisy.a - clean.a) < 0.02f)
        assertTrue("b a bougé de ${abs(noisy.b - clean.b)}", abs(noisy.b - clean.b) < 0.02f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `points source degeneres`() {
        val same = floatArrayOf(5f, 5f, 5f, 5f, 5f, 5f, 5f, 5f, 5f, 5f)
        similarityTransform(same, ARCFACE_TEMPLATE_112)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `longueurs incoherentes`() {
        similarityTransform(floatArrayOf(0f, 0f), ARCFACE_TEMPLATE_112)
    }
}
