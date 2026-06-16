package com.example.estimateserver.repository;

import com.example.estimateserver.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    List<Project> findByObjectId(String objectId);

    List<Project> findByCreatedBy(String userId);

    List<Project> findByOwnerId(String ownerId);

    // ===== НОВЫЕ МЕТОДЫ ДЛЯ РАЗДЕЛЕНИЯ ПО ПОЛЬЗОВАТЕЛЯМ =====

    // Найти все проекты пользователя
    List<Project> findByUserId(String userId);

    // Найти проекты пользователя по objectId
    List<Project> findByUserIdAndObjectId(String userId, String objectId);

    // Найти проекты пользователя по ownerId
    List<Project> findByUserIdAndOwnerId(String userId, String ownerId);

    // Найти проекты пользователя по createdBy
    List<Project> findByUserIdAndCreatedBy(String userId, String createdBy);

    // Найти все проекты пользователя с сортировкой
    @Query("SELECT p FROM Project p WHERE p.userId = :userId ORDER BY p.updatedAt DESC")
    List<Project> findAllByUserIdOrderByUpdatedAtDesc(@Param("userId") String userId);

    // Найти проекты по списку ID (для расшаренных проектов)
    @Query("SELECT p FROM Project p WHERE p.id IN :projectIds AND p.userId = :userId")
    List<Project> findProjectsByIdsAndUserId(@Param("projectIds") List<String> projectIds, @Param("userId") String userId);

    // Обновлённый метод для поиска по контактам (с учётом владельца)
    @Query("SELECT DISTINCT p FROM Project p " +
            "LEFT JOIN Contact c ON c.id = p.customerContactId OR c.id = p.foremanContactId OR c.id = p.managerContactId " +
            "LEFT JOIN ContactMethod cm ON cm.contactId = c.id " +
            "WHERE (cm.value LIKE CONCAT('%', :phoneNumber, '%') " +
            "OR p.ownerId = (SELECT u.id FROM User u WHERE u.phoneNumber = :phoneNumber)) " +
            "AND p.userId = :userId")
    List<Project> findProjectsByContactPhoneForUser(@Param("phoneNumber") String phoneNumber, @Param("userId") String userId);

    // Расшаренные проекты для пользователя
    @Query("SELECT p FROM Project p WHERE p.id IN " +
            "(SELECT sp.projectId FROM SharedProject sp WHERE sp.sharedWithUserId = :userId) " +
            "OR p.userId = :userId")
    List<Project> findAllAccessibleProjectsForUser(@Param("userId") String userId);

    // Проверить, имеет ли пользователь доступ к проекту
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Project p " +
            "WHERE p.id = :projectId AND (p.userId = :userId OR " +
            "p.id IN (SELECT sp.projectId FROM SharedProject sp WHERE sp.projectId = :projectId AND sp.sharedWithUserId = :userId))")
    boolean hasUserAccessToProject(@Param("projectId") String projectId, @Param("userId") String userId);

    @Query("SELECT DISTINCT p FROM Project p " +
            "LEFT JOIN Contact c ON c.id = p.customerContactId OR c.id = p.foremanContactId OR c.id = p.managerContactId " +
            "LEFT JOIN ContactMethod cm ON cm.contactId = c.id " +
            "WHERE cm.value LIKE CONCAT('%', :phoneNumber, '%') " +
            "OR p.ownerId = (SELECT u.id FROM User u WHERE u.phoneNumber = :phoneNumber)")
    List<Project> findProjectsByContactPhone(@Param("phoneNumber") String phoneNumber);
}