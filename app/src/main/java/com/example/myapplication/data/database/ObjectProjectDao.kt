//package com.example.myapplication.data.database
//
//import androidx.room.*
//import com.example.myapplication.data.models.ObjectProject
//import kotlinx.coroutines.flow.Flow
//
//@Dao
//interface ObjectProjectDao {
//    @Query("SELECT * FROM object_projects WHERE objectId = :objectId")
//    fun getProjectsForObject(objectId: String): Flow<List<ObjectProject>>
//
//    @Insert
//    suspend fun insertObjectProject(objectProject: ObjectProject)
//
//    @Delete
//    suspend fun deleteObjectProject(objectProject: ObjectProject)
//}
