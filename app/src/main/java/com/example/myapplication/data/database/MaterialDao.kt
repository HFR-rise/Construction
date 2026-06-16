package com.example.myapplication.data.database

import androidx.room.*
import com.example.myapplication.data.models.Material
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {

    @Query("DELETE FROM materials WHERE id = :materialId")
    suspend fun deleteMaterialById(materialId: String)

    @Query("SELECT * FROM materials WHERE projectId = :projectId")
    fun getMaterialsForProject(projectId: String): Flow<List<Material>>

    @Insert
    suspend fun insertMaterial(material: Material)

    @Update
    suspend fun updateMaterial(material: Material)

    @Delete
    suspend fun deleteMaterial(material: Material)

    @Query("SELECT SUM(quantity * unitPrice) FROM materials WHERE projectId = :projectId")
    suspend fun getTotalMaterialCost(projectId: String): Double?

    @Query("DELETE FROM materials")
    suspend fun deleteAll()

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: String): Material?

}