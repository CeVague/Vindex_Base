package com.cevague.vindex.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cevague.vindex.data.database.entity.Person
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    // Queries - reactive

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersons(): Flow<List<Person>>

    @Query("SELECT * FROM persons WHERE name IS NOT NULL ORDER BY name ASC")
    fun getNamedPersons(): Flow<List<Person>>

    @Query("SELECT * FROM persons WHERE name IS NULL")
    fun getUnnamedPersons(): Flow<List<Person>>

    @Query("SELECT * FROM persons WHERE id = :id")
    fun getPersonById(id: Long): Flow<Person?>

    @Query("SELECT COUNT(*) FROM persons")
    fun getPersonCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM persons WHERE name IS NULL")
    fun getUnnamedPersonCount(): Flow<Int>

    // Queries - one-shot

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getPersonByIdOnce(id: Long): Person?

    @Query("SELECT * FROM persons WHERE name = :name LIMIT 1")
    suspend fun getPersonByName(name: String): Person?

    @Query("SELECT * FROM persons")
    suspend fun getAllPersonsOnce(): List<Person>

    // Insert

    @Insert
    suspend fun insert(person: Person): Long

    // Update

    @Update
    suspend fun update(person: Person)

    @Query("UPDATE persons SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String?)

    @Query("UPDATE persons SET photo_count = :count WHERE id = :id")
    suspend fun updatePhotoCount(id: Long, count: Int)

    @Query("UPDATE persons SET centroid_embedding = :embedding, centroid_updated_at = :timestamp WHERE id = :id")
    suspend fun updateCentroid(id: Long, embedding: ByteArray?, timestamp: Long)

    @Query("""
        UPDATE persons SET photo_count = (
            SELECT COUNT(DISTINCT f.photo_id) FROM faces f WHERE f.person_id = persons.id
        )
    """)
    suspend fun recalculateAllPhotoCounts()

    @Query("""
        UPDATE persons SET photo_count = (
            SELECT COUNT(DISTINCT f.photo_id) FROM faces f WHERE f.person_id = :id
        ) WHERE id = :id
    """)
    suspend fun recalculatePhotoCount(id: Long)

    // Delete

    @Delete
    suspend fun delete(person: Person)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM persons WHERE photo_count = 0")
    suspend fun deleteEmpty()
}
