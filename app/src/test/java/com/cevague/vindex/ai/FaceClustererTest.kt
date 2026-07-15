package com.cevague.vindex.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'assignation décide, en trois issues, du sort de chaque visage. Une bande mal
 * bornée n'échoue jamais bruyamment : elle pollue les personnes (seuil trop bas)
 * ou éparpille les groupes (trop haut), et ça ne se voit qu'à l'usage. Les
 * frontières sont donc figées ici, sur des vecteurs unitaires dont la similarité
 * cosinus se calcule à la main.
 */
class FaceClustererTest {

    private val high = 0.75f
    private val medium = 0.60f

    /** Vecteur unitaire de référence : sa similarité à `[x, y]` vaut simplement x. */
    private val face = floatArrayOf(1f, 0f)

    private fun person(id: Long, x: Float, y: Float, named: Boolean = true, photoCount: Int = 0) =
        PersonCentroid(id, floatArrayOf(x, y), named, photoCount)

    private fun assertAuto(expectedId: Long, expectedSimilarity: Float, actual: Assignment) {
        assertTrue("attendu Auto, obtenu $actual", actual is Assignment.Auto)
        actual as Assignment.Auto
        assertEquals(expectedId, actual.personId)
        assertEquals(expectedSimilarity, actual.similarity, 0.001f)
    }

    private fun assertPending(expectedId: Long, expectedSimilarity: Float, actual: Assignment) {
        assertTrue("attendu Pending, obtenu $actual", actual is Assignment.Pending)
        actual as Assignment.Pending
        assertEquals(expectedId, actual.personId)
        assertEquals(expectedSimilarity, actual.similarity, 0.001f)
    }

    /** Le tout premier visage d'une galerie neuve : personne à qui se comparer. */
    @Test
    fun `aucune personne connue donne une nouvelle personne`() {
        assertEquals(Assignment.NewPerson, assignFace(face, emptyList(), high, medium))
    }

    @Test
    fun `un centroide identique est assigne automatiquement`() {
        val result = assignFace(face, listOf(person(7L, 1f, 0f)), high, medium)
        assertAuto(7L, 1f, result)
    }

    @Test
    fun `au dessus du seuil haut on assigne`() {
        val result = assignFace(face, listOf(person(7L, 0.8f, 0.6f)), high, medium)
        assertAuto(7L, 0.8f, result)
    }

    @Test
    fun `entre medium et high on propose`() {
        val result = assignFace(face, listOf(person(7L, 0.7f, 0.7141f)), high, medium)
        assertPending(7L, 0.7f, result)
    }

    @Test
    fun `sous le seuil medium on cree une nouvelle personne`() {
        val orthogonal = person(7L, 0f, 1f)
        assertEquals(Assignment.NewPerson, assignFace(face, listOf(orthogonal), high, medium))
    }

    /** Les bornes sont inclusives : pile sur le seuil, on prend la décision la plus forte. */
    @Test
    fun `les seuils sont inclusifs`() {
        assertAuto(7L, 0.75f, assignFace(face, listOf(person(7L, 0.75f, 0.6614f)), high, medium))
        assertPending(7L, 0.60f, assignFace(face, listOf(person(7L, 0.60f, 0.8f)), high, medium))
    }

    /** Le cœur de la fonction : c'est la MEILLEURE personne qui gagne, pas la première. */
    @Test
    fun `la meilleure personne l'emporte quel que soit l'ordre`() {
        val far = person(1L, 0f, 1f)        // 0.0
        val close = person(2L, 0.8f, 0.6f)  // 0.8
        val best = person(3L, 1f, 0f)       // 1.0

        assertAuto(3L, 1f, assignFace(face, listOf(far, close, best), high, medium))
        assertAuto(3L, 1f, assignFace(face, listOf(best, close, far), high, medium))
        assertAuto(3L, 1f, assignFace(face, listOf(close, best, far), high, medium))
    }

    /**
     * On ne pose de question que sur une personne **nommée** : « est-ce Marie ? »
     * se répond, « est-ce le groupe #47 ? » non. Un groupe anonyme au seuil moyen
     * reste donc séparé, en attendant la proposition de fusion de groupes.
     */
    @Test
    fun `pas de suggestion sur un groupe sans nom`() {
        val anonyme = person(7L, 0.7f, 0.7141f, named = false)

        assertEquals(Assignment.NewPerson, assignFace(face, listOf(anonyme), high, medium))
    }

    /** Au-dessus du seuil haut, en revanche, le nom ne change rien : on assigne. */
    @Test
    fun `le seuil haut assigne meme un groupe sans nom`() {
        val anonyme = person(7L, 0.8f, 0.6f, named = false)

        assertAuto(7L, 0.8f, assignFace(face, listOf(anonyme), high, medium))
    }

    /** Une seule personne au-dessus du seuil parmi des candidats médiocres. */
    @Test
    fun `un seul candidat suffisant est retenu parmi des voisins mediocres`() {
        val centroids = listOf(
            person(1L, 0.1f, 0.995f),
            person(2L, 0.9f, 0.436f),
            person(3L, 0.2f, 0.980f)
        )
        assertAuto(2L, 0.9f, assignFace(face, centroids, high, medium))
    }

    // ------------------------------------------- contrainte « même photo »

    /**
     * Deux visages d'une même photo sont deux personnes différentes. La personne
     * déjà servie sort des candidats — même si c'est la plus ressemblante.
     */
    @Test
    fun `une personne exclue n'est plus candidate`() {
        val parfait = person(1L, 1f, 0f)

        assertAuto(1L, 1f, assignFace(face, listOf(parfait), high, medium))
        assertEquals(
            Assignment.NewPerson,
            assignFace(face, listOf(parfait), high, medium, excluded = setOf(1L))
        )
    }

    /**
     * Le cas qui motive la contrainte : deux frères sur une photo. Le premier
     * visage prend le groupe ; le second, qui lui ressemble assez pour dépasser
     * le seuil, doit se rabattre sur quelqu'un d'autre plutôt que d'y entrer.
     */
    @Test
    fun `le second meilleur l'emporte quand le meilleur est exclu`() {
        val frere = person(1L, 1f, 0f)      // 1.0
        val soeur = person(2L, 0.8f, 0.6f)  // 0.8

        assertAuto(2L, 0.8f, assignFace(face, listOf(frere, soeur), high, medium, setOf(1L)))
    }

    /** Toutes les personnes servies : le visage suivant crée forcément un groupe. */
    @Test
    fun `tout exclure donne une nouvelle personne`() {
        val centroids = listOf(person(1L, 1f, 0f), person(2L, 0.9f, 0.436f))

        assertEquals(
            Assignment.NewPerson,
            assignFace(face, centroids, high, medium, excluded = setOf(1L, 2L))
        )
    }

    // ----------------------------------------------------------- proposeMerges

    /** Seuil de la décision groupe↔groupe — sur un autre axe que high/medium. */
    private val floor = 0.40f

    private fun assertProposal(expectedKeep: Long, expectedMerge: Long, actual: MergeProposal) {
        assertEquals("gardé", expectedKeep, actual.keepId)
        assertEquals("absorbé", expectedMerge, actual.mergeId)
    }

    /** Le cas nominal : une personne scindée en deux par l'ordre d'arrivée. */
    @Test
    fun `deux groupes proches sont proposes a la fusion`() {
        val proposals = proposeMerges(
            listOf(person(1L, 1f, 0f, named = false), person(2L, 0.9f, 0.436f, named = false)),
            floor
        )

        assertEquals(1, proposals.size)
        assertEquals(0.9f, proposals[0].similarity, 0.001f)
    }

    /** Sous le plancher, l'app se tait : à l'utilisateur de lier à la main. */
    @Test
    fun `sous le plancher rien n'est propose`() {
        val proposals = proposeMerges(
            listOf(person(1L, 1f, 0f, named = false), person(2L, 0.3f, 0.954f, named = false)),
            floor
        )

        assertTrue("attendu aucune proposition, obtenu $proposals", proposals.isEmpty())
    }

    /** Le plancher est inclusif, comme les autres seuils. */
    @Test
    fun `le plancher est inclusif`() {
        val proposals = proposeMerges(
            listOf(person(1L, 1f, 0f, named = false), person(2L, 0.40f, 0.9165f, named = false)),
            floor
        )

        assertEquals(1, proposals.size)
    }

    /**
     * Deux noms distincts sont une affirmation de l'utilisateur, pas une hésitation :
     * même identiques, deux personnes nommées ne sont jamais proposées.
     */
    @Test
    fun `deux personnes nommees ne sont jamais proposees`() {
        val proposals = proposeMerges(
            listOf(person(1L, 1f, 0f, named = true), person(2L, 1f, 0f, named = true)),
            floor
        )

        assertTrue("attendu aucune proposition, obtenu $proposals", proposals.isEmpty())
    }

    /** Le nom est la seule information rare de la paire : il survit, quel que soit l'ordre. */
    @Test
    fun `la personne nommee est celle qu'on garde`() {
        val nommee = person(1L, 1f, 0f, named = true)
        val anonyme = person(2L, 0.9f, 0.436f, named = false, photoCount = 50)

        assertProposal(1L, 2L, proposeMerges(listOf(nommee, anonyme), floor).single())
        assertProposal(1L, 2L, proposeMerges(listOf(anonyme, nommee), floor).single())
    }

    /** Entre anonymes, le plus gros groupe garde son identité : son centroïde est mieux fondé. */
    @Test
    fun `entre anonymes le plus gros groupe est garde`() {
        val petit = person(1L, 1f, 0f, named = false, photoCount = 2)
        val gros = person(2L, 0.9f, 0.436f, named = false, photoCount = 30)

        assertProposal(2L, 1L, proposeMerges(listOf(petit, gros), floor).single())
        assertProposal(2L, 1L, proposeMerges(listOf(gros, petit), floor).single())
    }

    /**
     * Le cœur de l'appariement : A ressemble à B et à C, mais accepter A+B ferait
     * disparaître A — proposer A+C ensuite n'aurait plus de sens. La meilleure paire
     * gagne, le reste attend le prochain passage.
     */
    @Test
    fun `un groupe n'apparait que dans une proposition`() {
        val a = person(1L, 1f, 0f, named = false)
        val b = person(2L, 0.95f, 0.312f, named = false)   // 0.95 avec A
        val c = person(3L, 0.8f, 0.6f, named = false)      // 0.80 avec A

        val proposals = proposeMerges(listOf(a, b, c), floor)

        assertEquals(1, proposals.size)
        assertEquals(0.95f, proposals[0].similarity, 0.001f)
        assertTrue("C ne doit pas être servi", 3L !in setOf(proposals[0].keepId, proposals[0].mergeId))
    }

    /** Deux paires disjointes se proposent en même temps, la meilleure d'abord. */
    @Test
    fun `des paires disjointes sont toutes proposees`() {
        val proposals = proposeMerges(
            listOf(
                person(1L, 1f, 0f, named = false),
                person(2L, 0.99f, 0.141f, named = false),   // 0.99 avec 1
                person(3L, 0f, 1f, named = false),
                person(4L, 0.141f, 0.99f, named = false)    // 0.99 avec 3, ~0.14 avec 1
            ),
            floor
        )

        assertEquals(2, proposals.size)
        assertTrue(proposals[0].similarity >= proposals[1].similarity)
    }

    /** Rien à comparer : aucune paire n'existe. */
    @Test
    fun `un seul groupe ou aucun ne propose rien`() {
        assertTrue(proposeMerges(emptyList(), floor).isEmpty())
        assertTrue(proposeMerges(listOf(person(1L, 1f, 0f, named = false)), floor).isEmpty())
    }

    // ------------------------------------------------------- weightedCentroid

    /** Un seul visage : le centroïde est ce visage, renormalisé. */
    @Test
    fun `un seul vecteur donne sa propre direction`() {
        val centroid = weightedCentroid(listOf(floatArrayOf(3f, 4f)), listOf(1f))

        assertEquals(0.6f, centroid[0], 0.001f)
        assertEquals(0.8f, centroid[1], 0.001f)
    }

    /** Poids égaux : la moyenne tombe pile entre les deux. */
    @Test
    fun `poids egaux donnent la direction mediane`() {
        val centroid = weightedCentroid(
            listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)),
            listOf(1f, 1f)
        )

        assertEquals(0.7071f, centroid[0], 0.001f)
        assertEquals(0.7071f, centroid[1], 0.001f)
    }

    /** Le point de la pondération : un visage confirmé à la main tire le centroïde à lui. */
    @Test
    fun `un poids fort tire le centroide vers son vecteur`() {
        val centroid = weightedCentroid(
            listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)),
            listOf(3f, 1f)
        )

        // Somme = [3, 1], norme = sqrt(10)
        assertEquals(0.9487f, centroid[0], 0.001f)
        assertEquals(0.3162f, centroid[1], 0.001f)
    }

    /** Le résultat est toujours unitaire : il sera comparé par produit scalaire. */
    @Test
    fun `le centroide est normalise L2`() {
        val centroid = weightedCentroid(
            listOf(floatArrayOf(2f, 0f), floatArrayOf(0f, 5f)),
            listOf(2f, 7f)
        )

        val norm = centroid[0] * centroid[0] + centroid[1] * centroid[1]
        assertEquals(1f, norm, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `aucun vecteur est une erreur`() {
        weightedCentroid(emptyList(), emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `autant de poids que de vecteurs`() {
        weightedCentroid(listOf(floatArrayOf(1f, 0f)), listOf(1f, 1f))
    }

    /** Sans cette garde, le centroïde serait nul et la personne ne matcherait plus rien. */
    @Test(expected = IllegalArgumentException::class)
    fun `un poids total nul est une erreur`() {
        weightedCentroid(listOf(floatArrayOf(1f, 0f)), listOf(0f))
    }
}
