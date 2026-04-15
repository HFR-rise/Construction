package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.database.*
import com.example.myapplication.data.models.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val materialDao: MaterialDao,
    private val workItemDao: WorkItemDao,
    private val contactDao: ContactDao,
    private val contactMethodDao: ContactMethodDao,
//    private val transactionDao: TransactionDao
) {

    // ==================== подтягивание сметы ====================

    fun getAllProjects(): Flow<List<Project>> = projectDao.getAllProjects()

    suspend fun getProjectById(projectId: String): Project? {
        Log.d("ProjectRepository", "getProjectById: $projectId")
        return projectDao.getProjectById(projectId)
    }

    fun getProjectsByObjectId(objectId: String): Flow<List<Project>> {
        Log.d("ProjectRepository", "getProjectsByObjectId called for objectId: $objectId")
        return projectDao.getProjectsByObjectId(objectId)
    }

    suspend fun createProject(project: Project): Project {
        projectDao.insertProject(project)
        return project
    }

    suspend fun updateProject(project: Project) = projectDao.updateProject(project)

    suspend fun deleteProject(project: Project) = projectDao.deleteProject(project)

    suspend fun updateProjectObjectId(projectId: String, newObjectId: String) {
        projectDao.updateProjectObjectId(projectId, newObjectId)
    }

    // ==================== Для материалов ====================

    fun getMaterials(projectId: String): Flow<List<Material>> {
        Log.d("ProjectRepository", "getMaterials for project: $projectId")
        return materialDao.getMaterialsForProject(projectId)
    }

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

    // ==================== Для работы ====================

    fun getWorkItems(projectId: String): Flow<List<WorkItem>> {
        Log.d("ProjectRepository", "getWorkItems for project: $projectId")
        return workItemDao.getWorkItemsForProject(projectId)
    }

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

    // ==================== Для контактов ====================

    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun addContact(contact: Contact): String {
        contactDao.insertContact(contact)
        return contact.id
    }

    suspend fun updateContact(contact: Contact) = contactDao.updateContact(contact)

    suspend fun deleteContact(contact: Contact) = contactDao.deleteContact(contact)

    suspend fun getContactsCount(): Int = contactDao.getContactsCount()

    // ==================== Для способов связи ====================

    fun getContactMethods(contactId: String): Flow<List<ContactMethod>> =
        contactMethodDao.getMethodsForContact(contactId)

    suspend fun addContactMethod(method: ContactMethod) = contactMethodDao.insertContactMethod(method)

    suspend fun updateContactMethod(method: ContactMethod) = contactMethodDao.updateContactMethod(method)

    suspend fun deleteContactMethod(method: ContactMethod) = contactMethodDao.deleteContactMethod(method)

    // ==================== Транзакции(нереализовано) ====================

//    fun getTransactions(projectId: String): Flow<List<Transaction>> =
//        transactionDao.getTransactionsForProject(projectId)
//
//    suspend fun addTransaction(transaction: Transaction) = transactionDao.insertTransaction(transaction)
//
//    suspend fun updateTransaction(transaction: Transaction) = transactionDao.updateTransaction(transaction)
//
//    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.deleteTransaction(transaction)

    // ==================== Обновление ====================

    private suspend fun updateProjectTotal(projectId: String) {
        val totalMaterial = materialDao.getTotalMaterialCost(projectId) ?: 0.0
        val totalWork = workItemDao.getTotalWorkCost(projectId) ?: 0.0
        val totalBudget = totalMaterial + totalWork
        projectDao.updateProjectFinances(projectId, totalBudget, 0.0)
    }
}