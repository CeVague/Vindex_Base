package com.cevague.vindex.data.repository

import androidx.room.Transaction
import com.cevague.vindex.ai.weightedCentroid
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.search.asFloatArray
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

    fun getPeopleForTrombinoscope(): Flow<List<PersonDao.PersonWithCover>> =
        personDao.getPeopleForTrombinoscope(SUSPECT_SCORE_BELOW)


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
     * « Ce n'est pas une personne » sur un groupe entier : ses visages passent en
     * `ignored` et le groupe disparaît.
     *
     * L'ordre compte. Marquer d'abord, supprimer ensuite : la FK `person_id` est en
     * `SET_NULL`, donc supprimer la personne en premier effacerait le lien qui sert
     * justement à retrouver ses visages.
     *
     * Sans ça, écarter un chat imposait de supprimer le groupe puis d'écarter ses
     * visages un par un dans la file d'identification.
     */
    @Transaction
    suspend fun ignorePersonAsNotAPerson(personId: Long) {
        faceDao.markAllAsIgnoredForPerson(personId)
        personDao.deleteById(personId)
    }

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

    suspend fun markAsIgnored(id: Long) = faceDao.markAsIgnored(id)


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

        val centroid = weightedCentroid(
            faces.map { face ->
                val blob = face.embedding!!
                blob.asFloatArray(blob.size / Float.SIZE_BYTES)
            },
            faces.map { it.weight }
        )
        updateCentroid(personId, centroid.toEmbeddingBlob())
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
