package com.example.myapplication.data.database

import androidx.room.*
import com.example.myapplication.data.models.WorkItem
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkItemDao {

    @Query("DELETE FROM work_items WHERE id = :workItemId")
    suspend fun deleteWorkItemById(workItemId: String)

    @Query("SELECT * FROM work_items WHERE projectId = :projectId ORDER BY stage ASC")
    fun getWorkItemsForProject(projectId: String): Flow<List<WorkItem>>

    @Query("SELECT SUM(laborHours * hourlyRate + materialCost) FROM work_items WHERE projectId = :projectId")
    suspend fun getTotalWorkCost(projectId: String): Double?

    @Insert
    suspend fun insertWorkItem(workItem: WorkItem)

    @Update
    suspend fun updateWorkItem(workItem: WorkItem)

    @Delete
    suspend fun deleteWorkItem(workItem: WorkItem)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("SELECT * FROM work_items WHERE id = :id")
    suspend fun getWorkItemById(id: String): WorkItem?


}