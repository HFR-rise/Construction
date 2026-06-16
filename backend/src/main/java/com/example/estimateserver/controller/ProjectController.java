package com.example.estimateserver.controller;

import com.example.estimateserver.model.Material;
import com.example.estimateserver.model.Project;
import com.example.estimateserver.model.WorkItem;
import com.example.estimateserver.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "*")
public class ProjectController {

    private final SyncService syncService;

    public ProjectController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/pending/{userId}")
    public ResponseEntity<List<Project>> getPendingShares(@PathVariable String userId) {
        List<Project> projects = syncService.getPendingProjectsForUser(userId);
        return ResponseEntity.ok(projects);
    }

    @PostMapping("/{projectId}/accept")
    public ResponseEntity<Void> acceptShare(@PathVariable String projectId,
                                            @RequestHeader("X-User-Id") String userId) {
        syncService.acceptShare(projectId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{projectId}/decline")
    public ResponseEntity<Void> declineShare(@PathVariable String projectId,
                                             @RequestHeader("X-User-Id") String userId) {
        syncService.declineShare(projectId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects(@RequestHeader("X-User-Id") String userId) {
        List<Project> projects = syncService.getProjectsForUser(userId);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(@PathVariable String id,
                                              @RequestHeader("X-User-Id") String userId) {
        Optional<Project> project = syncService.getProject(id);
        if (project.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!syncService.hasAccessToProject(id, userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(project.get());
    }

    // ===== ИСПРАВЛЕННЫЙ МЕТОД =====
    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody Project project,
                                                 @RequestHeader("X-User-Id") String userId) {
        try {
            System.out.println("=== CREATE/UPDATE PROJECT ===");
            System.out.println("Project ID: " + project.getId());
            System.out.println("User ID: " + userId);

            // Очищаем userId из тела запроса (доверяем заголовку)
            project.setUserId(null);

            // Проверяем, существует ли проект
            Optional<Project> existing = syncService.getProject(project.getId());

            Project result;
            if (existing.isPresent()) {
                // ✅ ОБНОВЛЯЕМ СУЩЕСТВУЮЩИЙ ПРОЕКТ
                System.out.println("Project exists, updating...");
                project.setUserId(existing.get().getUserId());
                result = syncService.updateProject(project, userId);
                System.out.println("✅ Project updated: " + result.getId());
            } else {
                // ✅ СОЗДАЁМ НОВЫЙ ПРОЕКТ
                System.out.println("Project does not exist, creating...");
                result = syncService.createProject(project, userId);
                System.out.println("✅ Project created: " + result.getId());
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("❌ Error in createProject: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> updateProject(@PathVariable String id,
                                                 @RequestBody Project project,
                                                 @RequestHeader("X-User-Id") String userId) {
        project.setId(id);
        Project updated = syncService.updateProject(project, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable String id,
                                              @RequestHeader("X-User-Id") String userId) {
        syncService.deleteProject(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{projectId}/materials")
    public ResponseEntity<List<Material>> getMaterials(@PathVariable String projectId,
                                                       @RequestHeader("X-User-Id") String userId) {
        if (!syncService.hasAccessToProject(projectId, userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(syncService.getMaterials(projectId));
    }

    @GetMapping("/{projectId}/work-items")
    public ResponseEntity<List<WorkItem>> getWorkItems(@PathVariable String projectId,
                                                       @RequestHeader("X-User-Id") String userId) {
        if (!syncService.hasAccessToProject(projectId, userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(syncService.getWorkItems(projectId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Project>> getProjectsForUser(@PathVariable String userId) {
        List<Project> projects = syncService.getProjectsForUser(userId);
        return ResponseEntity.ok(projects);
    }

    @PostMapping("/{projectId}/share")
    public ResponseEntity<Void> shareProject(@PathVariable String projectId,
                                             @RequestBody Map<String, String> request,
                                             @RequestHeader("X-User-Id") String userId) {
        String phoneNumber = request.get("phoneNumber");
        syncService.shareProjectWithUser(projectId, phoneNumber, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/shared/{userId}")
    public ResponseEntity<List<Project>> getSharedProjects(@PathVariable String userId) {
        List<Project> projects = syncService.getProjectsSharedWithUser(userId);
        return ResponseEntity.ok(projects);
    }
}