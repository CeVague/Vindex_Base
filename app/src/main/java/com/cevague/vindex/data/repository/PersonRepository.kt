package com.cevague.vindex.data.repository

import androidx.room.Transaction
import com.cevague.vindex.ai.weightedCentroid
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.search.asFloatArray
import com.cevague.vindex.search.dotProduct
import com.cevague.vindex.search.toEmbeddingBlob
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepository @Inject constructor(
    private val personDao: PersonDao,
    private val faceDao: FaceDao
) {

    // Person queries - reactive

    fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()

    fun getAllPersonSummary(): Flow<List<PersonDao.PersonSummary>> =
        personDao.getAllPersonsSummary()

    fun getNamedPersons(): Flow<List<Person>> = personDao.getNamedPersons()

    fun getPeopleForTrombinoscope(includeHidden: Boolean): Flow<List<PersonDao.PersonWithCover>> =
        personDao.getPeopleForTrombinoscope(SUSPECT_SCORE_BELOW, includeHidden)


    fun getNamedPersonsSummary(): Flow<List<PersonDao.PersonSummary>> =
        personDao.getNamedPersonsSummary()

    fun getUnnamedPersons(): Flow<List<Person>> = personDao.getUnnamedPersons()

    fun getUnnamedPersonsSummary(): Flow<List<PersonDao.PersonSummary>> =
        personDao.getUnnamedPersonsSummary()

    fun getPersonById(id: Long): Flow<Person?> = personDao.getPersonById(id)

    fun getPersonNamesForPhoto(photoId: Long): Flow<List<String>> =
        personDao.getPersonNamesForPhoto(photoId)

    fun getPersonCount(): Flow<Int> = personDao.getPersonCount()

    fun getUnnamedPersonCount(): Flow<Int> = personDao.getUnnamedPersonCount()

    /** Groupes anonymes restant à nommer (cf. le DAO : ce n'est PAS le compte des `pending`). */
    fun getGroupsToNameCount(): Flow<Int> = personDao.getGroupsToNameCount()

    suspend fun getNextGroupToName(excludeIds: Set<Long>): PersonDao.GroupToName? =
        personDao.getNextGroupToName(excludeIds)

    // Person queries - one-shot

    suspend fun getPersonByIdOnce(id: Long): Person? = personDao.getPersonByIdOnce(id)

    suspend fun getPersonByName(name: String): Person? = personDao.getPersonByName(name)

    suspend fun getAllPersonsOnce(): List<Person> = personDao.getAllPersonsOnce()

    suspend fun getAllPersonSummaryOnce(): List<PersonDao.PersonSummary> =
        personDao.getAllPersonSummaryOnce()

    // Person insert/update/delete

    suspend fun insert(person: Person): Long {
        val formattedName = formatName(person.name)
        return personDao.insert(person.copy(name = formattedName))
    }

    @Transaction
    suspend fun getOrCreatePersonByName(name: String?): Long {
        val formattedName = formatName(name) ?: return createPerson(null)

        val existing = personDao.getPersonByName(formattedName)
        return existing?.id ?: personDao.insert(
            Person(
                name = formattedName,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun createPerson(name: String? = null): Long {
        val formattedName = formatName(name)
        val person = Person(
            name = formattedName,
            createdAt = System.currentTimeMillis()
        )
        return personDao.insert(person)
    }

    suspend fun updateName(id: Long, name: String?) {
        val formattedName = formatName(name)
        personDao.updateName(id, formattedName)
    }

    suspend fun updateCentroid(id: Long, embedding: ByteArray?) =
        personDao.updateCentroid(id, embedding, System.currentTimeMillis())

    suspend fun delete(person: Person) = personDao.delete(person)

    @Transaction
    suspend fun deletePersonAndResetFaces(personId: Long) {
        faceDao.unassignFromPerson(personId)
        personDao.deleteById(personId)
    }

    /**
     * Écarte un groupe entier : ses visages passent en `ignored` avec [reason]
     * (`Face.EXCLUDED_*`) et le groupe disparaît.
     *
     * L'ordre compte. Marquer d'abord, supprimer ensuite : la FK `person_id` est en
     * `SET_NULL`, donc supprimer la personne en premier effacerait le lien qui sert
     * justement à retrouver ses visages.
     *
     * Sans ça, écarter un chat imposait de supprimer le groupe puis d'écarter ses
     * visages un par un dans la file d'identification.
     */
    @Transaction
    suspend fun excludePerson(personId: Long, reason: String) {
        faceDao.markAllAsIgnoredForPerson(personId, reason)
        personDao.deleteById(personId)
    }

    /**
     * Masque un inconnu — ou le ré-affiche.
     *
     * Ne touche **pas** à ses visages, délibérément : le groupe reste vivant et
     * continue d'absorber ses nouvelles apparitions. C'est ce qui fait qu'on ne le
     * revoit jamais. Le supprimer aurait l'effet inverse : ses visages, relâchés,
     * reformeraient un groupe visible à chaque analyse.
     */
    suspend fun setPersonHidden(personId: Long, hidden: Boolean) =
        personDao.setHidden(personId, hidden)

    suspend fun deleteAllPersons() = personDao.deleteAll()

    suspend fun deleteAllFaces() = faceDao.deleteAll()

    /**
     * Ardoise vierge avant une ré-analyse des visages (changement de détecteur ou
     * d'embedder).
     *
     * Les centroïdes doivent partir **avec** les visages : ils sont exprimés dans
     * l'espace vectoriel de l'ancien modèle, et rien dans `assignFace` ne peut le
     * deviner — il comparerait les nouveaux embeddings à d'anciens repères, et
     * distribuerait les visages n'importe où en ayant l'air de fonctionner.
     *
     * Les personnes elles-mêmes survivent : les non nommées seront balayées par le
     * `CleanupWorker` en fin de chaîne, les nommées restent (vides) — leur nom est
     * la seule chose qu'un changement de modèle ne périme pas.
     */
    @Transaction
    suspend fun resetFaceData() {
        faceDao.deleteAll()
        personDao.clearAllCentroids()
    }

    suspend fun deleteById(id: Long) = personDao.deleteById(id)

    /** Recalcule les compteurs dénormalisés (après suppression de photos). */
    suspend fun recalculatePhotoCounts() = personDao.recalculateAllPhotoCounts()

    /** Supprime les personnes vides non nommées (garde les nommées). */
    suspend fun deleteEmptyUnnamed() = personDao.deleteEmptyUnnamed()

    // Face queries - reactive

    fun getFacesByPhoto(photoId: Long): Flow<List<Face>> = faceDao.getFacesByPhoto(photoId)

    fun getFaceSummariesByPhoto(photoId: Long): Flow<List<FaceDao.FaceSummary>> =
        faceDao.getFaceSummariesByPhoto(photoId)


    fun getFacesByPerson(personId: Long): Flow<List<Face>> = faceDao.getFacesByPerson(personId)

    fun getFacesSummaryByPerson(personId: Long): Flow<List<FaceDao.FaceSummary>> =
        faceDao.getFacesSummaryByPerson(personId)

    fun getUnidentifiedFaces(): Flow<List<Face>> = faceDao.getUnidentifiedFaces()

    fun getPendingFaces(): Flow<List<Face>> = faceDao.getPendingFaces()

    fun getPendingFaceCount(): Flow<Int> = faceDao.getPendingFaceCount()

    // Face queries - one-shot

    suspend fun getFacesByPhotoOnce(photoId: Long): List<Face> =
        faceDao.getFacesByPhotoOnce(photoId)

    suspend fun getFacesByPersonOnce(personId: Long): List<Face> =
        faceDao.getFacesByPersonOnce(personId)

    suspend fun getFaceByIdOnce(id: Long): Face? = faceDao.getFaceByIdOnce(id)

    suspend fun getAllIdentifiedFacesWithEmbedding(): List<Face> =
        faceDao.getAllIdentifiedFacesWithEmbedding()

    /** Jeu de mesure : visages identifiés, hors personnes masquées (cf. le DAO). */
    suspend fun getFacesForCalibration(): List<Face> = faceDao.getFacesForCalibration()

    /** Idem, avec le chemin de la photo : pour ré-embarquer avec un autre modèle. */
    suspend fun getLabeledFacesWithPhoto(): List<FaceDao.LabeledFace> =
        faceDao.getLabeledFacesWithPhoto()

    suspend fun getCoverPhotoPathForPerson(personId: Long): String? =
        faceDao.getCoverPhotoPathForPerson(personId)

    suspend fun getPrimaryFaceWithPhoto(personId: Long): FaceDao.FaceWithPhoto? =
        faceDao.getPrimaryFaceWithPhoto(personId)

    suspend fun getPendingFaceWithPhoto(): List<FaceDao.FaceWithPhoto> =
        faceDao.getPendingFaceWithPhoto()

    suspend fun getNextPendingFaceWithPhoto(): FaceDao.FaceWithPhoto? =
        faceDao.getNextPendingFaceWithPhoto()


    suspend fun getNextPendingFaceExcluding(excludeIds: Set<Long>): FaceDao.FaceWithPhoto? =
        faceDao.getNextPendingFaceExcluding(excludeIds)

    suspend fun markAsIgnored(id: Long, reason: String) = faceDao.markAsIgnored(id, reason)


    // Face insert/update/delete

    suspend fun insertFace(face: Face): Long = faceDao.insert(face)

    suspend fun insertFaces(faces: List<Face>): List<Long> = faceDao.insertAll(faces)

    suspend fun assignFaceToPerson(
        faceId: Long,
        personId: Long?,
        assignmentType: String,
        confidence: Float?,
        weight: Float
    ) {
        faceDao.assignToPerson(
            id = faceId,
            personId = personId,
            assignmentType = assignmentType,
            confidence = confidence,
            weight = weight,
            timestamp = System.currentTimeMillis()
        )
        personId?.let { personDao.recalculatePhotoCount(it) }
    }

    suspend fun updateFaceEmbedding(id: Long, embedding: ByteArray?, model: String?) =
        faceDao.updateEmbedding(id, embedding, model)

    suspend fun deleteFace(face: Face) = faceDao.delete(face)

    suspend fun deleteFaceById(id: Long) = faceDao.deleteById(id)

    suspend fun deleteFacesByPhoto(photoId: Long) = faceDao.deleteByPhoto(photoId)

    // Merge two persons into one
    @Transaction
    suspend fun mergePersons(keepId: Long, mergeId: Long) {
        faceDao.reassignAllFaces(oldPersonId = mergeId, newPersonId = keepId)
        personDao.recalculatePhotoCount(keepId)
        personDao.deleteById(mergeId)
        // Sans ça, la personne fusionnée garderait le centroïde de sa seule moitié
        // d'origine : elle continuerait d'attirer — et de rater — comme avant.
        recomputeCentroid(keepId)
    }

    /**
     * Recalcule le centroïde d'une personne depuis ses visages, plutôt que de
     * l'incrémenter : la moyenne est **pondérée** et le schéma ne stocke pas la
     * somme des poids. Seuls `auto` et `manual` y entrent — un `pending` est une
     * question, pas une réponse.
     *
     * La dimension se déduit du BLOB lui-même (float32) : inutile de la faire
     * descendre depuis le modèle actif.
     */
    suspend fun recomputeCentroid(personId: Long) {
        val faces = faceDao.getFacesByPersonOnce(personId).filter {
            it.embedding != null &&
                    (it.assignmentType == Face.ASSIGNMENT_AUTO || it.assignmentType == Face.ASSIGNMENT_MANUAL)
        }
        if (faces.isEmpty()) return

        val vectors = faces.map { face ->
            val blob = face.embedding!!
            blob.asFloatArray(blob.size / Float.SIZE_BYTES)
        }
        val centroid = weightedCentroid(vectors, faces.map { it.weight })
        updateCentroid(personId, centroid.toEmbeddingBlob())
        updatePrimaryFace(faces, vectors, centroid)
    }

    /**
     * Élit la vignette de la personne : le visage le plus **proche du centroïde**,
     * donc le plus typique — celui qui la représente le mieux.
     *
     * Les requêtes de couverture retombaient jusqu'ici sur `confidence DESC`, qui est
     * la confiance du **détecteur** : la vignette était donc le visage le mieux *vu*,
     * pas le plus *reconnaissable*. Un profil net gagnait contre un portrait de face
     * légèrement moins bien détecté.
     *
     * Élu ici parce que c'est le seul endroit qui connaît le centroïde : la réponse
     * change dès qu'il bouge, et la recalculer ailleurs le referait pour rien.
     */
    private suspend fun updatePrimaryFace(
        faces: List<Face>,
        vectors: List<FloatArray>,
        centroid: FloatArray
    ) {
        var bestIndex = 0
        var bestSimilarity = Float.NEGATIVE_INFINITY
        for (i in faces.indices) {
            val similarity = dotProduct(vectors[i], centroid)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestIndex = i
            }
        }
        // Un seul primaire par personne : l'ancien doit tomber, sinon deux vignettes
        // se disputeraient le `LIMIT 1` et la couverture deviendrait arbitraire.
        faces.forEachIndexed { i, face ->
            val shouldBePrimary = i == bestIndex
            if (face.isPrimary != shouldBePrimary) {
                faceDao.setPrimary(face.id, shouldBePrimary)
            }
        }
    }

    companion object {
        /**
         * Un groupe anonyme dont même le meilleur visage est détecté sous ce score
         * part en fin de trombinoscope : c'est là que se trouvent les animaux, les
         * statues et les portraits. Frontière **mesurée** sur la galerie de test
         * (2026-07-15) — mais un vrai visage, net, y traînait à 0,68, d'où un simple
         * tri plutôt qu'un seuil de détection : rien n'est perdu, tout est relu.
         */
        const val SUSPECT_SCORE_BELOW = 0.70f
    }

    private fun formatName(name: String?): String? {
        val formatted = name?.trim()?.split(" ")
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        return if (formatted.isNullOrEmpty()) null else formatted
    }
}
