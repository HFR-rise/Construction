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
    suspend fun insertObject(obj: ObjectModel)

    @Update
    suspend fun updateObject(obj: ObjectModel)

    @Delete
    suspend fun deleteObject(obj: ObjectModel)
}