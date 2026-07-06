package com.cevague.vindex.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class VectorUtilsTest {

    @Test
    fun `encode puis decode preserve le vecteur (round-trip)`() {
        val v = floatArrayOf(0f, 1f, -2.5f, 3.14159f, Float.MIN_VALUE, -0.001f)
        val restored = v.toEmbeddingBlob().asFloatArray(v.size)
        assertEquals(v.size, restored.size)
        for (i in v.indices) assertEquals(v[i], restored[i], 0f)
    }

    @Test
    fun `le blob fait exactement 4 octets par float`() {
        assertEquals(512 * 4, FloatArray(512).toEmbeddingBlob().size)
    }

    @Test
    fun `le blob est little-endian`() {
        // 1.0f = 0x3F800000 ; en little-endian : 00 00 80 3F.
        val blob = floatArrayOf(1.0f).toEmbeddingBlob()
        assertEquals(0x00.toByte(), blob[0])
        assertEquals(0x00.toByte(), blob[1])
        assertEquals(0x80.toByte(), blob[2])
        assertEquals(0x3F.toByte(), blob[3])
    }

    @Test
    fun `normalizeL2 ramene la norme a 1`() {
        val v = floatArrayOf(3f, 4f) // norme 5
        v.normalizeL2()
        assertEquals(0.6f, v[0], 1e-6f)
        assertEquals(0.8f, v[1], 1e-6f)
        val norm = sqrt((v[0] * v[0] + v[1] * v[1]).toDouble())
        assertEquals(1.0, norm, 1e-6)
    }

    @Test
    fun `normalizeL2 laisse un vecteur nul inchange (pas de NaN)`() {
        val v = floatArrayOf(0f, 0f, 0f)
        v.normalizeL2()
        assertTrue(v.all { it == 0f })
    }

    @Test
    fun `dotProduct de vecteurs orthonormes vaut 0, colineaires vaut 1`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, dotProduct(a, b), 1e-6f)
        assertEquals(1f, dotProduct(a, a), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dotProduct rejette des dimensions incompatibles`() {
        dotProduct(floatArrayOf(1f, 2f), floatArrayOf(1f))
    }

    @Test
    fun `topK garde les K meilleurs, tries par score decroissant`() {
        val top = TopKCollector(3)
        listOf(10L to 0.1f, 20L to 0.9f, 30L to 0.5f, 40L to 0.7f, 50L to 0.2f)
            .forEach { (id, s) -> top.offer(id, s) }

        val result = top.toSortedList()
        assertEquals(listOf(20L, 40L, 30L), result.map { it.id })
        assertEquals(3, top.size)
    }

    @Test
    fun `topK avec moins d'elements que K renvoie tout, trie`() {
        val top = TopKCollector(5)
        top.offer(1L, 0.3f)
        top.offer(2L, 0.8f)
        assertEquals(listOf(2L, 1L), top.toSortedList().map { it.id })
    }

    @Test
    fun `topK avec K=0 ne retient rien`() {
        val top = TopKCollector(0)
        top.offer(1L, 1f)
        assertTrue(top.toSortedList().isEmpty())
    }
}
