package com.cevague.vindex.data.repository

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

    fun getNamedPersons(): Flow<List<Person>> = personDao.getNamedPersons()

    fun getUnnamedPersons(): Flow<List<Person>> = personDao.getUnnamedPersons()

    fun getPersonById(id: Long): Flow<Person?> = personDao.getPersonById(id)

    fun getPersonCount(): Flow<Int> = personDao.getPersonCount()

    fun getUnnamedPersonCount(): Flow<Int> = personDao.getUnnamedPersonCount()

    // Person queries - one-shot

    suspend fun getPersonByIdOnce(id: Long): Person? = personDao.getPersonByIdOnce(id)

    suspend fun getPersonByName(name: String): Person? = personDao.getPersonByName(name)

    suspend fun getAllPersonsOnce(): List<Person> = personDao.getAllPersonsOnce()

    // Person insert/update/delete

    suspend fun insert(person: Person): Long = personDao.insert(person)

    suspend fun createPerson(name: String? = null): Long {
        val person = Person(
            name = name,
            createdAt = System.currentTimeMillis()
        )
        return personDao.insert(person)
    }

    suspend fun updateName(id: Long, name: String?) = personDao.updateName(id, name)

    suspend fun updateCentroid(id: Long, embedding: ByteArray?) =
        personDao.updateCentroid(id, embedding, System.currentTimeMillis())

    suspend fun delete(person: Person) = personDao.delete(person)

    suspend fun deleteById(id: Long) = personDao.deleteById(id)

    suspend fun deleteEmpty() = personDao.deleteEmpty()

    // Face queries - reactive

    fun getFacesByPhoto(photoId: Long): Flow<List<Face>> = faceDao.getFacesByPhoto(photoId)

    fun getFacesByPerson(personId: Long): Flow<List<Face>> = faceDao.getFacesByPerson(personId)

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
        // Recalculate photo count for the person
        personId?.let { personDao.recalculatePhotoCount(it) }
    }

    suspend fun updateFaceEmbedding(id: Long, embedding: ByteArray?, model: String?) =
        faceDao.updateEmbedding(id, embedding, model)

    suspend fun deleteFace(face: Face) = faceDao.delete(face)

    suspend fun deleteFacesByPhoto(photoId: Long) = faceDao.deleteByPhoto(photoId)

    // Merge two persons into one
    suspend fun mergePersons(keepId: Long, mergeId: Long) {
        faceDao.reassignAllFaces(oldPersonId = mergeId, newPersonId = keepId)
        personDao.recalculatePhotoCount(keepId)
        personDao.deleteById(mergeId)
    }
}
