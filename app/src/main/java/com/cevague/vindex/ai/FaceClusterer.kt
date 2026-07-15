package com.cevague.vindex.ai

import com.cevague.vindex.search.dotProduct
import com.cevague.vindex.search.normalizeL2

data class PersonCentroid(
    val personId: Long,
    val centroid: FloatArray,
    /** Seule une personne nommée peut faire l'objet d'une question à l'utilisateur. */
    val named: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersonCentroid

        if (personId != other.personId) return false
        if (named != other.named) return false
        if (!centroid.contentEquals(other.centroid)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = personId.hashCode()
        result = 31 * result + named.hashCode()
        result = 31 * result + centroid.contentHashCode()
        return result
    }
}

sealed interface Assignment {
    data class Auto(val personId: Long, val similarity: Float) : Assignment
    data class Pending(val personId: Long, val similarity: Float) : Assignment
    data object NewPerson : Assignment
}

/**
 * Décide du sort d'un visage face aux personnes connues.
 *
 * Au-dessus de [high], on assigne sans demander. Au-dessus de [medium], on ne
 * pose la question que si la personne est **nommée** : « est-ce Marie ? » se
 * répond, « est-ce le groupe #47 ? » n'a aucun sens pour l'utilisateur. Un
 * groupe sans nom au seuil moyen reste donc séparé — la proposition de fusion
 * de groupes (à venir) est faite pour les réunir. Sinon, nouvelle personne.
 *
 * [excluded] retire des candidats des personnes déjà servies. L'appelant s'en
 * sert pour une raison de fond : **deux visages d'une même photo sont deux
 * personnes différentes**. Sans ça, deux frères sur une photo peuvent tous deux
 * dépasser le seuil pour le même groupe, et l'un des deux sera forcément faux —
 * d'autant plus que le seuil est bas.
 */
fun assignFace(
    embedding: FloatArray,
    centroids: List<PersonCentroid>,
    high: Float,
    medium: Float,
    excluded: Set<Long> = emptySet()
): Assignment {
    var best: PersonCentroid? = null
    var bestSimilarity = Float.NEGATIVE_INFINITY
    for (candidate in centroids) {
        if (candidate.personId in excluded) continue
        val similarity = dotProduct(embedding, candidate.centroid)
        if (similarity > bestSimilarity) {
            bestSimilarity = similarity
            best = candidate
        }
    }
    val winner = best ?: return Assignment.NewPerson

    if (bestSimilarity >= high) return Assignment.Auto(winner.personId, bestSimilarity)
    if (bestSimilarity >= medium && winner.named) {
        return Assignment.Pending(winner.personId, bestSimilarity)
    }
    return Assignment.NewPerson
}

/**
 * Centroïde d'une personne : moyenne **pondérée** de ses vecteurs, renormalisée L2.
 *
 * Le poids traduit la confiance — un visage confirmé à la main est la seule
 * vérité terrain, il doit peser plus qu'une assignation automatique, elle-même
 * pondérée par sa similarité. Seuls les visages `auto` et `manual` doivent
 * arriver ici : un `pending` est une **question**, pas une réponse, et
 * corromprait la personne avant qu'on y ait répondu.
 *
 * Pas de division par la somme des poids : la renormalisation L2 qui suit
 * absorbe toute échelle. On somme, on normalise.
 */
fun weightedCentroid(vectors: List<FloatArray>, weights: List<Float>): FloatArray {
    require(vectors.isNotEmpty()) { "aucun vecteur pour ce centroïde" }
    require(vectors.size == weights.size) {
        "${vectors.size} vecteurs pour ${weights.size} poids"
    }

    val dim = vectors[0].size
    val sum = FloatArray(dim)
    var totalWeight = 0f

    for (i in vectors.indices) {
        val vector = vectors[i]
        require(vector.size == dim) { "dimensions hétérogènes : $dim puis ${vector.size}" }
        val weight = weights[i]
        totalWeight += weight
        for (j in 0 until dim) sum[j] += weight * vector[j]
    }
    // Somme nulle -> centroïde nul -> la personne cesserait de matcher quoi que ce
    // soit, en silence. On préfère l'exception.
    require(totalWeight > 0f) { "poids total nul" }

    return sum.normalizeL2()
}