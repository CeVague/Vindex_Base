package com.cevague.vindex.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cevague.vindex.data.database.entity.Person
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    data class PersonSummary(
        @ColumnInfo(name = "id") val id: Long,
        @ColumnInfo(name = "name") val name: String?,
        @ColumnInfo(name = "photo_count") val photoCount: Int
    )

    data class PersonWithCover(
        val id: Long,
        val name: String?,
        val photoCount: Int,
        val coverPath: String?,
        val boxLeft: Float?,
        val boxTop: Float?,
        val boxRight: Float?,
        val boxBottom: Float?
    )

    // Queries - reactive

    @Query("""
    SELECT p.id, p.name, p.photo_count as photoCount, ph.file_path as coverPath, f.box_left as boxLeft, f.box_top as boxTop, f.box_right as boxRight, f.box_bottom as boxBottom
    FROM persons p
    LEFT JOIN faces f ON f.id = (
        SELECT id FROM faces 
        WHERE person_id = p.id 
        ORDER BY is_primary DESC, id ASC 
        LIMIT 1
    )
    LEFT JOIN photos ph ON f.photo_id = ph.id
    WHERE p.name IS NOT NULL
    ORDER BY p.name ASC
""")
    fun getNamedPersonsWithCover(): Flow<List<PersonWithCover>>

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersons(): Flow<List<Person>>

    @Query("SELECT id, name, photo_count FROM persons ORDER BY name ASC")
    fun getAllPersonsSummary(): Flow<List<PersonSummary>>

    @Query("SELECT * FROM persons WHERE name IS NOT NULL ORDER BY name ASC")
    fun getNamedPersons(): Flow<List<Person>>

    @Query("SELECT id, name, photo_count FROM persons WHERE name IS NOT NULL ORDER BY name ASC")
    fun getNamedPersonsSummary(): Flow<List<PersonSummary>>

    @Query("SELECT * FROM persons WHERE name IS NULL")
    fun getUnnamedPersons(): Flow<List<Person>>

    @Query("SELECT id, name, photo_count FROM persons WHERE name IS NULL")
    fun getUnnamedPersonsSummary(): Flow<List<PersonSummary>>

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

    @Query("SELECT id, name, photo_count FROM persons")
    suspend fun getAllPersonSummaryOnce(): List<PersonSummary>

    // Insert

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(person: Person): Long

    // Update

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(person: Person)

    @Query("UPDATE persons SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String?)

    @Query("UPDATE persons SET photo_count = :count WHERE id = :id")
    suspend fun updatePhotoCount(id: Long, count: Int)

    @Query("UPDATE persons SET centroid_embedding = :embedding, centroid_updated_at = :timestamp WHERE id = :id")
    suspend fun updateCentroid(id: Long, embedding: ByteArray?, timestamp: Long)

    @Query(
        """
        UPDATE persons SET photo_count = (
            SELECT COUNT(DISTINCT f.photo_id) FROM faces f WHERE f.person_id = persons.id
        )
    """
    )
    suspend fun recalculateAllPhotoCounts()

    @Query(
        """
        UPDATE persons SET photo_count = (
            SELECT COUNT(DISTINCT f.photo_id) FROM faces f WHERE f.person_id = :id
        ) WHERE id = :id
    """
    )
    suspend fun recalculatePhotoCount(id: Long)

    // Delete

    @Delete
    suspend fun delete(person: Person)

    @Query("DELETE FROM persons")
    suspend fun deleteAll()

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM persons WHERE photo_count = 0")
    suspend fun deleteEmpty()
}
