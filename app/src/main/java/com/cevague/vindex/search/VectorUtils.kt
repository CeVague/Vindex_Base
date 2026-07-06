package com.cevague.vindex.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue
import kotlin.math.sqrt

/**
 * Utilitaires de la recherche vectorielle (ARCHITECTURE.md §6, phase 2 §4.7).
 *
 * Format de stockage d'un embedding : float32 little-endian, normalisé L2 à
 * l'écriture. Les vecteurs étant normalisés, la similarité cosinus se réduit au
 * produit scalaire — d'où la force brute assumée du scan vectoriel.
 */

/** Décode un BLOB float32 little-endian en `FloatArray` de [dim] éléments. */
fun ByteArray.asFloatArray(dim: Int): FloatArray {
    val out = FloatArray(dim)
    ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
    return out
}

/** Encode un `FloatArray` en BLOB float32 little-endian (format de stockage Room). */
fun FloatArray.toEmbeddingBlob(): ByteArray {
    val bytes = ByteArray(size * Float.SIZE_BYTES)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(this)
    return bytes
}

/**
 * Normalise le vecteur L2 **en place** et le renvoie. Un vecteur de norme nulle
 * est laissé inchangé (pas de division par zéro / NaN).
 */
fun FloatArray.normalizeL2(): FloatArray {
    var sumSq = 0.0
    for (v in this) sumSq += v.toDouble() * v
    val norm = sqrt(sumSq)
    if (norm > 0.0) {
        val inv = (1.0 / norm).toFloat()
        for (i in indices) this[i] *= inv
    }
    return this
}

/** Produit scalaire de deux vecteurs de même dimension (= cosinus si normalisés). */
fun dotProduct(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Dimensions incompatibles: ${a.size} vs ${b.size}" }
    var sum = 0f
    for (i in a.indices) sum += a[i] * b[i]
    return sum
}

/**
 * Collecteur top-K borné : conserve les K meilleurs `(id, score)` parmi tout ce
 * qui lui est proposé, en mémoire O(K) via un tas-min. Alimenté au fil des chunks
 * du scan vectoriel (`offer` par candidat), résultats lus en fin de scan.
 */
class TopKCollector(private val k: Int) {

    data class Scored(val id: Long, val score: Float)

    // Tas-min sur le score : la racine est le plus faible des K meilleurs courants.
    private val heap = PriorityQueue<Scored>(k.coerceAtLeast(1), compareBy { it.score })

    fun offer(id: Long, score: Float) {
        if (k <= 0) return
        if (heap.size < k) {
            heap.add(Scored(id, score))
        } else if (score > heap.peek()!!.score) { // heap plein (taille k ≥ 1) → racine non nulle
            heap.poll()
            heap.add(Scored(id, score))
        }
    }

    /** Les meilleurs résultats retenus, triés par score décroissant. */
    fun toSortedList(): List<Scored> = heap.sortedByDescending { it.score }

    val size: Int get() = heap.size
}
