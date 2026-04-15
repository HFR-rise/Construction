package com.example.myapplication.data.database

import androidx.room.*
import com.example.myapplication.data.models.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE objectId = :objectId")
    fun getProjectsByObjectId(objectId: String): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): Project?

    @Insert
    suspend fun insertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("UPDATE projects SET objectId = :newObjectId WHERE id = :projectId")
    suspend fun updateProjectObjectId(projectId: String, newObjectId: String)

    @Query("UPDATE projects SET totalBudget = :budget, totalSpent = :spent WHERE id = :projectId")
    suspend fun updateProjectFinances(projectId: String, budget: Double, spent: Double)
}