package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.database.ObjectDao
import com.example.myapplication.data.database.ProjectDao
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.utils.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectRepository @Inject constructor(
    private val objectDao: ObjectDao,
    private val projectDao: ProjectDao,
    private val userPreferences: UserPreferences
) {

    // ===== GET МЕТОДЫ (Flow) =====
    fun getRootObjects(): Flow<List<ObjectModel>> = objectDao.getRootObjects()
    fun getChildObjects(parentId: String): Flow<List<ObjectModel>> = objectDao.getChildObjects(parentId)
    suspend fun getObjectById(id: String): ObjectModel? = objectDao.getObjectById(id)
    fun getProjectsForObject(objectId: String): Flow<List<Project>> = projectDao.getProjectsByObjectId(objectId)
    fun getAllObjects(): Flow<List<ObjectModel>> = objectDao.getAllObjects()

    // ===== GET МЕТОДЫ (однократная загрузка) =====
    suspend fun getRootObjectsOnce(): List<ObjectModel> = getRootObjects().first()
    suspend fun getChildObjectsOnce(parentId: String): List<ObjectModel> = getChildObjects(parentId).first()
    suspend fun getAllObjectsOnce(): List<ObjectModel> = getAllObjects().first()

    // ===== ВСТАВКА И ОБНОВЛЕНИЕ =====
    suspend fun insertObject(objectModel: ObjectModel): Long = objectDao.insertObject(objectModel)
    suspend fun updateObject(objectModel: ObjectModel) = objectDao.updateObject(objectModel)

    // ===== КАСКАДНОЕ УДАЛЕНИЕ ОБЪЕКТА (УДАЛЯЕТ ВСЕ СВЯЗАННЫЕ СМЕТЫ) =====
    suspend fun deleteObjectWithCascade(objectModel: ObjectModel): DeletionResult {
        Log.d("ObjectRepository", "=== STARTING CASCADE DELETE (WITH PROJECTS DELETION) ===")
        Log.d("ObjectRepository", "Deleting object: ${objectModel.name} (${objectModel.id})")

        var totalProjectsDeleted = 0
        var totalMaterialsDeleted = 0
        var totalWorkItemsDeleted = 0

        // 1. Рекурсивно получаем ВСЕ дочерние объекты
        val allChildObjects = getAllChildObjectsRecursive(objectModel.id)
        Log.d("ObjectRepository", "Found ${allChildObjects.size} child objects to delete")

        // 2. Собираем все ID объектов для удаления (включая родительский)
        val allObjectIds = listOf(objectModel.id) + allChildObjects.map { it.id }

        // 3. ДЛЯ КАЖДОГО ОБЪЕКТА УДАЛЯЕМ ВСЕ СВЯЗАННЫЕ СМЕТЫ (вместе с материалами и работами)
        for (objId in allObjectIds) {
            val result = deleteAllProjectsForObject(objId)
            totalProjectsDeleted += result.projectsDeleted
            totalMaterialsDeleted += result.materialsDeleted
            totalWorkItemsDeleted += result.workItemsDeleted
            Log.d("ObjectRepository", "Deleted ${result.projectsDeleted} projects from object $objId")
        }

        // 4. Удаляем все объекты
        for (objId in allObjectIds) {
            objectDao.deleteObjectById(objId)
            Log.d("ObjectRepository", "Deleted object: $objId")
        }

        val result = DeletionResult(
            objectsDeleted = allObjectIds.size,
            projectsDeleted = totalProjectsDeleted,
            materialsDeleted = totalMaterialsDeleted,
            workItemsDeleted = totalWorkItemsDeleted
        )

        Log.d("ObjectRepository", "=== CASCADE DELETE COMPLETED ===")
        Log.d("ObjectRepository", "Deleted ${result.objectsDeleted} objects, ${result.projectsDeleted} projects, " +
                "${result.materialsDeleted} materials, ${result.workItemsDeleted} work items")

        return result
    }

    // Удаляет ВСЕ проекты, привязанные к объекту (вместе с материалами и работами)
    private suspend fun deleteAllProjectsForObject(objectId: String): ProjectDeletionResult {
        val projects = projectDao.getProjectsByObjectId(objectId).first()

        var materialsDeleted = 0
        var workItemsDeleted = 0

        for (project in projects) {
            // Удаляем все материалы проекта
            val materials = projectDao.getMaterialsForProject(project.id).first()
            for (material in materials) {
                projectDao.deleteMaterialById(material.id)
                materialsDeleted++
            }

            // Удаляем все работы проекта
            val workItems = projectDao.getWorkItemsForProject(project.id).first()
            for (workItem in workItems) {
                projectDao.deleteWorkItemById(workItem.id)
                workItemsDeleted++
            }

            // Удаляем сам проект
            projectDao.deleteProject(project)
            Log.d("ObjectRepository", "Deleted project: ${project.name} (${project.id})")
        }

        return ProjectDeletionResult(
            projectsDeleted = projects.size,
            materialsDeleted = materialsDeleted,
            workItemsDeleted = workItemsDeleted
        )
    }

    // Рекурсивно получаем все дочерние объекты
    private suspend fun getAllChildObjectsRecursive(parentId: String): List<ObjectModel> {
        val allChildren = mutableListOf<ObjectModel>()
        val directChildren = objectDao.getChildObjects(parentId).first()

        for (child in directChildren) {
            allChildren.add(child)
            allChildren.addAll(getAllChildObjectsRecursive(child.id))
        }

        return allChildren
    }

    // ===== ПРОСТОЕ УДАЛЕНИЕ (без каскада, только объект) =====
    suspend fun deleteObjectSimple(objectModel: ObjectModel) = objectDao.deleteObject(objectModel)

    // ===== УДАЛЕНИЕ ОДНОГО ПРОЕКТА С ЕГО ДАННЫМИ =====
    suspend fun deleteProjectWithAllData(project: Project): ProjectDeletionResult {
        Log.d("ObjectRepository", "Deleting project with all data: ${project.name}")

        var materialsDeleted = 0
        var workItemsDeleted = 0

        // Удаляем все материалы проекта
        val materials = projectDao.getMaterialsForProject(project.id).first()
        for (material in materials) {
            projectDao.deleteMaterialById(material.id)
            materialsDeleted++
        }

        // Удаляем все работы проекта
        val workItems = projectDao.getWorkItemsForProject(project.id).first()
        for (workItem in workItems) {
            projectDao.deleteWorkItemById(workItem.id)
            workItemsDeleted++
        }

        // Удаляем сам проект
        projectDao.deleteProject(project)

        Log.d("ObjectRepository", "Deleted project with $materialsDeleted materials and $workItemsDeleted work items")

        return ProjectDeletionResult(
            projectsDeleted = 1,
            materialsDeleted = materialsDeleted,
            workItemsDeleted = workItemsDeleted
        )
    }

    // ===== ОЧИСТКА "БИТЫХ" ССЫЛОК (перемещает в корневой объект) =====
    suspend fun cleanupOrphanedProjects(): Int {
        Log.d("ObjectRepository", "Starting cleanup of orphaned projects")

        val rootObjectId = getOrCreateRootObjectId()
        val allObjects = getAllObjectsOnce()
        val validObjectIds = allObjects.map { it.id }.toSet()

        // Находим проекты с битыми ссылками
        val allProjects = projectDao.getAllProjectsOnce()
        val orphanedProjects = allProjects.filter { project ->
            project.objectId != null &&
                    project.objectId !in validObjectIds &&
                    project.objectId != rootObjectId
        }

        Log.d("ObjectRepository", "Found ${orphanedProjects.size} orphaned projects")

        // Перемещаем их в корневой объект (НЕ УДАЛЯЕМ!)
        for (project in orphanedProjects) {
            val fixedProject = project.copy(objectId = rootObjectId)
            projectDao.updateProject(fixedProject)
            Log.d("ObjectRepository", "Fixed orphaned project: ${project.name} -> moved to root")
        }

        return orphanedProjects.size
    }

    // Получаем или создаем корневой объект "Без объекта"
    private suspend fun getOrCreateRootObjectId(): String {
        val rootObjects = getRootObjectsOnce()
        val existingRoot = rootObjects.find { it.name == "Без объекта" }

        if (existingRoot != null) {
            return existingRoot.id
        }

        // Создаем новый корневой объект
        val newRoot = ObjectModel(
            name = "Без объекта",
            description = "Корневые сметы (автоматически создан)",
            userId = userPreferences.getUserId() ?: ""
        )
        objectDao.insertObject(newRoot)
        Log.d("ObjectRepository", "Created new root object: ${newRoot.id}")
        return newRoot.id
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    suspend fun getTotalObjectsCount(): Int {
        return objectDao.getAllObjectsSync().size
    }

    suspend fun getTotalProjectsCount(): Int {
        return projectDao.getAllProjectsSync().size
    }

    suspend fun insertOrUpdateObject(objectModel: ObjectModel) {
        val existing = objectDao.getObjectById(objectModel.id)
        if (existing == null) {
            objectDao.insertObject(objectModel)
            Log.d("ObjectRepository", "Inserted new object: ${objectModel.name}")
        } else {
            objectDao.updateObject(objectModel)
            Log.d("ObjectRepository", "Updated existing object: ${objectModel.name}")
        }
    }

    suspend fun deleteAllObjects() {
        objectDao.deleteAll()
    }

    suspend fun getRootObjectId(): String? {
        val rootObjects = getRootObjectsOnce()
        return rootObjects.find { it.name == "Без объекта" }?.id
    }

    suspend fun deleteChildObjects(parentId: String) {
        objectDao.deleteChildObjects(parentId)
    }

    suspend fun deleteRootObjects() {
        objectDao.deleteRootObjects()
    }
}

// Результат удаления объекта
data class DeletionResult(
    val objectsDeleted: Int,
    val projectsDeleted: Int,
    val materialsDeleted: Int,
    val workItemsDeleted: Int
) {
    fun getTotalItemsDeleted(): Int = objectsDeleted + projectsDeleted + materialsDeleted + workItemsDeleted

    override fun toString(): String {
        return "Удалено: объектов: $objectsDeleted, смет: $projectsDeleted, материалов: $materialsDeleted, работ: $workItemsDeleted"
    }
}

// Результат удаления проекта
data class ProjectDeletionResult(
    val projectsDeleted: Int,
    val materialsDeleted: Int,
    val workItemsDeleted: Int
)