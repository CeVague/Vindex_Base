package com.cevague.vindex.data.repository

import androidx.room.Transaction
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.Person
import kotlinx.coroutines.flow.Flow

class PersonRepository(
    private val personDao: PersonDao,
    private val faceDao: FaceDao
) {

    // Person queries - reactive

    fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()

    fun getAllPersonSummary(): Flow<List<PersonDao.PersonSummary>> =
        personDao.getAllPersonsSummary()

    fun getNamedPersons(): Flow<List<Person>> = personDao.getNamedPersons()

    fun getNamedPersonsWithCover(): Flow<List<PersonDao.PersonWithCover>> = personDao.getNamedPersonsWithCover()


    fun getNamedPersonsSummary(): Flow<List<PersonDao.PersonSummary>> =
        personDao.getNamedPersonsSummary()

    fun getUnnamedPersons(): Flow<List<Person>> = personDao.getUnnamedPersons()

    fun getUnnamedPersonsSummary(): Flow<List<PersonDao.PersonSummary>> =
        personDao.getUnnamedPersonsSummary()

    fun getPersonById(id: Long): Flow<Person?> = personDao.getPersonById(id)

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
    suspend fun deleteAllPersons() = personDao.deleteAll()

    suspend fun deleteAllFaces() = faceDao.deleteAll()

    suspend fun deleteById(id: Long) = personDao.deleteById(id)

    suspend fun deleteEmpty() = personDao.deleteEmpty()

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
    }

    private fun formatName(name: String?): String? {
        val formatted = name?.trim()?.split(" ")
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        return if (formatted.isNullOrEmpty()) null else formatted
    }
}
