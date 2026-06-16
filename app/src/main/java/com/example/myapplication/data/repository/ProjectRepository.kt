package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.database.*
import com.example.myapplication.data.models.*
import com.example.myapplication.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val materialDao: MaterialDao,
    private val workItemDao: WorkItemDao,
    private val contactDao: ContactDao,
    private val contactMethodDao: ContactMethodDao,
    private val apiService: ApiService
) {

    // ==================== GET МЕТОДЫ (Flow) ====================

    fun getAllProjects(): Flow<List<Project>> = projectDao.getAllProjects()

    fun getProjectsByObjectId(objectId: String): Flow<List<Project>> {
        Log.d("ProjectRepository", "getProjectsByObjectId called for objectId: $objectId")
        return projectDao.getProjectsByObjectId(objectId)
    }

    fun getProjectsForUser(userId: String): Flow<List<Project>> {
        return projectDao.getProjectsForUser(userId)
    }

    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()

    fun getContactMethods(contactId: String): Flow<List<ContactMethod>> =
        contactMethodDao.getMethodsForContact(contactId)

    fun getMaterials(projectId: String): Flow<List<Material>> {
        Log.d("ProjectRepository", "getMaterials for project: $projectId")
        return materialDao.getMaterialsForProject(projectId)
    }

    fun getWorkItems(projectId: String): Flow<List<WorkItem>> {
        Log.d("ProjectRepository", "getWorkItems for project: $projectId")
        return workItemDao.getWorkItemsForProject(projectId)
    }

    // ==================== GET МЕТОДЫ (однократная загрузка) ====================

    suspend fun getProjectById(projectId: String): Project? {
        Log.d("ProjectRepository", "getProjectById: $projectId")
        return projectDao.getProjectById(projectId)
    }

    suspend fun getProjectsByObjectIdOnce(objectId: String): List<Project> {
        return getProjectsByObjectId(objectId).first()
    }

    suspend fun getProjectsForUserOnce(userId: String): List<Project> {
        return getProjectsForUser(userId).first()
    }

    suspend fun getAllProjectsOnce(): List<Project> {
        return getAllProjects().first()
    }

    suspend fun getMaterialsOnce(projectId: String): List<Material> {
        return getMaterials(projectId).first()
    }

    suspend fun getWorkItemsOnce(projectId: String): List<WorkItem> {
        return getWorkItems(projectId).first()
    }

    suspend fun getAllContactsOnce(): List<Contact> {
        return getAllContacts().first()
    }

    suspend fun getContactMethodsOnce(contactId: String): List<ContactMethod> {
        return getContactMethods(contactId).first()
    }

    // ==================== ПРОЕКТЫ (CRUD) ====================

    suspend fun createProject(project: Project): Project {
        projectDao.insertProject(project)
        return project
    }

    suspend fun updateProject(project: Project) = projectDao.updateProject(project)

    suspend fun deleteProject(project: Project) = projectDao.deleteProject(project)

    suspend fun updateProjectObjectId(projectId: String, newObjectId: String) {
        projectDao.updateProjectObjectId(projectId, newObjectId)
    }

    suspend fun insertProject(project: Project) {
        projectDao.insertProject(project)
    }

    // ==================== МАТЕРИАЛЫ ====================

    suspend fun addMaterial(material: Material) {
        materialDao.insertMaterial(material)
        updateProjectTotal(material.projectId)
    }

    suspend fun updateMaterial(material: Material) {
        materialDao.updateMaterial(material)
        updateProjectTotal(material.projectId)
    }

    suspend fun deleteMaterial(material: Material) {
        materialDao.deleteMaterial(material)
        updateProjectTotal(material.projectId)
    }

    // ==================== РАБОТЫ ====================

    suspend fun addWorkItem(workItem: WorkItem) {
        workItemDao.insertWorkItem(workItem)
        updateProjectTotal(workItem.projectId)
    }

    suspend fun updateWorkItem(workItem: WorkItem) {
        workItemDao.updateWorkItem(workItem)
        updateProjectTotal(workItem.projectId)
    }

    suspend fun deleteWorkItem(workItem: WorkItem) {
        workItemDao.deleteWorkItem(workItem)
        updateProjectTotal(workItem.projectId)
    }

    // ==================== КОНТАКТЫ ====================

    suspend fun addContact(contact: Contact): String {
        contactDao.insertContact(contact)
        return contact.id
    }

    suspend fun updateContact(contact: Contact) = contactDao.updateContact(contact)

    suspend fun deleteContact(contact: Contact) = contactDao.deleteContact(contact)

    suspend fun getContactsCount(): Int = contactDao.getContactsCount()

    // ==================== СПОСОБЫ СВЯЗИ ====================

    suspend fun addContactMethod(method: ContactMethod) = contactMethodDao.insertContactMethod(method)

    suspend fun updateContactMethod(method: ContactMethod) = contactMethodDao.updateContactMethod(method)

    suspend fun deleteContactMethod(method: ContactMethod) = contactMethodDao.deleteContactMethod(method)

    // ==================== РАСШАРИВАНИЕ ====================

    suspend fun getPendingProjects(userId: String): Response<List<Project>> {
        return apiService.getPendingProjects(userId)
    }

    suspend fun acceptShare(projectId: String, userId: String): Response<Unit> {
        return apiService.acceptShare(projectId, userId)
    }

    suspend fun declineShare(projectId: String, userId: String): Response<Unit> {
        return apiService.declineShare(projectId, userId)
    }

    suspend fun shareProject(projectId: String, phoneNumber: String, userId: String): Response<Unit> {
        return apiService.shareProject(projectId, mapOf("phoneNumber" to phoneNumber), userId)
    }

    // ==================== СИНХРОНИЗАЦИЯ ====================

    suspend fun syncProjectsFromServer(userId: String): List<Project> {
        return try {
            val response = apiService.getProjectsForUser(userId)
            if (response.isSuccessful) {
                val projects = response.body() ?: emptyList()
                projectDao.deleteAll()
                projects.forEach { project ->
                    projectDao.insertProject(project)
                }
                projects
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Error syncing projects: ${e.message}")
            emptyList()
        }
    }

    // ==================== ОЧИСТКА ДАННЫХ ====================

//    suspend fun deleteAllProjectsForUser(userId: String) {
//        projectDao.deleteAllProjectsForUser(userId)
//    }

    suspend fun deleteAllProjects() {
        projectDao.deleteAll()
        materialDao.deleteAll()
        workItemDao.deleteAll()
    }

//    suspend fun clearAllDataForUser(userId: String) {
//        projectDao.deleteAllProjectsForUser(userId)
//        materialDao.deleteAllMaterialsForUser(userId)
//        workItemDao.deleteAllWorkItemsForUser(userId)
//    }

    // ==================== ОБНОВЛЕНИЕ БЮДЖЕТА ====================

    private suspend fun updateProjectTotal(projectId: String) {
        val totalMaterial = materialDao.getTotalMaterialCost(projectId) ?: 0.0
        val totalWork = workItemDao.getTotalWorkCost(projectId) ?: 0.0
        val totalBudget = totalMaterial + totalWork
        projectDao.updateProjectFinances(projectId, totalBudget, 0.0)
    }
}