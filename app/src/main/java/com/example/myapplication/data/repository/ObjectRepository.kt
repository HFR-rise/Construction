package com.example.myapplication.data.repository

import com.example.myapplication.data.database.*
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectRepository @Inject constructor(
    private val objectDao: ObjectDao,
    private val projectDao: ProjectDao
) {

    fun getRootObjects(): Flow<List<ObjectModel>> = objectDao.getRootObjects()

    fun getChildObjects(parentId: String): Flow<List<ObjectModel>> = objectDao.getChildObjects(parentId)

    suspend fun getObjectById(id: String): ObjectModel? = objectDao.getObjectById(id)

    suspend fun insertObject(objectModel: ObjectModel) = objectDao.insertObject(objectModel)

    suspend fun updateObject(objectModel: ObjectModel) = objectDao.updateObject(objectModel)

    suspend fun deleteObject(objectModel: ObjectModel) = objectDao.deleteObject(objectModel)

    fun getProjectsForObject(objectId: String): Flow<List<Project>> =
        projectDao.getProjectsByObjectId(objectId)
}