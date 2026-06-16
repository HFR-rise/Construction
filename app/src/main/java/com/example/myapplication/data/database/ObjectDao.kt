package com.example.myapplication.data.database

import androidx.room.*
import com.example.myapplication.data.models.ObjectModel
import kotlinx.coroutines.flow.Flow

@Dao
interface ObjectDao {
    @Query("SELECT * FROM objects WHERE parentObjectId IS NULL ORDER BY name ASC")
    fun getRootObjects(): Flow<List<ObjectModel>>

    @Query("SELECT * FROM objects WHERE parentObjectId = :parentId ORDER BY name ASC")
    fun getChildObjects(parentId: String): Flow<List<ObjectModel>>

    @Query("SELECT * FROM objects WHERE id = :id")
    suspend fun getObjectById(id: String): ObjectModel?

    @Insert
    suspend fun insertObject(obj: ObjectModel): Long

    @Update
    suspend fun updateObject(obj: ObjectModel)

    @Delete
    suspend fun deleteObject(obj: ObjectModel)

    @Query("DELETE FROM objects WHERE parentObjectId = :parentId")
    suspend fun deleteChildObjects(parentId: String)

    @Query("DELETE FROM objects WHERE parentObjectId IS NULL")
    suspend fun deleteRootObjects()

    @Query("DELETE FROM objects")
    suspend fun deleteAll()

//    @Query("DELETE FROM objects WHERE userId = :userId")
//    suspend fun deleteAllObjectsForUser(userId: String)

    @Query("DELETE FROM objects WHERE id = :objectId")
    suspend fun deleteObjectById(objectId: String)

    @Query("SELECT * FROM objects")
    fun getAllObjects(): Flow<List<ObjectModel>>

    @Query("SELECT * FROM objects")
    suspend fun getAllObjectsSync(): List<ObjectModel>



    suspend fun insertOrUpdateObject(obj: ObjectModel) {
        val existing = getObjectById(obj.id)
        if (existing == null) {
            insertObject(obj)
        } else {
            updateObject(obj)
        }
    }



//    @Query("DELETE FROM objects")
//    suspend fun deleteAllObjects()

}

