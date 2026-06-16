package com.example.myapplication.data.database

import androidx.room.*
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.WorkItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

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

//    @Query("DELETE FROM projects WHERE userId = :userId")
//    suspend fun deleteAllProjectsForUser(userId: String)

//    @Query("DELETE FROM projects")
//    suspend fun deleteAllProjects()


    @Query("SELECT * FROM projects WHERE userId = :userId")
    fun getProjectsForUser(userId: String): Flow<List<Project>>

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("SELECT * FROM projects WHERE userId = :userId")
    suspend fun getProjectsForUserSync(userId: String): List<Project>

    @Query("SELECT * FROM materials WHERE projectId = :projectId")
    fun getMaterialsForProject(projectId: String): Flow<List<Material>>

    @Query("SELECT * FROM work_items WHERE projectId = :projectId")
    fun getWorkItemsForProject(projectId: String): Flow<List<WorkItem>>

    @Query("DELETE FROM materials WHERE id = :materialId")
    suspend fun deleteMaterialById(materialId: String)

    @Query("DELETE FROM work_items WHERE id = :workItemId")
    suspend fun deleteWorkItemById(workItemId: String)

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsSync(): List<Project>

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsOnce(): List<Project>

    @Query("SELECT * FROM projects WHERE objectId = :objectId")
    suspend fun getProjectsByObjectIdOnce(objectId: String): List<Project>

    @Query("SELECT * FROM projects WHERE userId = :userId")
    suspend fun getProjectsForUserOnce(userId: String): List<Project>

}
