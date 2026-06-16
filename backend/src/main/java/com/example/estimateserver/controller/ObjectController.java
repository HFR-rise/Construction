package com.example.estimateserver.controller;

import com.example.estimateserver.model.ObjectModel;
import com.example.estimateserver.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/objects")
@CrossOrigin(origins = "*")
public class ObjectController {

    private final SyncService syncService;

    public ObjectController(SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * GET /api/objects/root
     * Получить корневые объекты ТОЛЬКО для текущего пользователя
     */
    @GetMapping("/root")
    public ResponseEntity<List<ObjectModel>> getRootObjects(@RequestHeader("X-User-Id") String userId) {
        List<ObjectModel> objects = syncService.getRootObjectsForUser(userId);
        return ResponseEntity.ok(objects);
    }

    /**
     * GET /api/objects/{parentId}/children
     * Получить дочерние объекты ТОЛЬКО для текущего пользователя
     */
    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<ObjectModel>> getChildObjects(@PathVariable String parentId,
                                                             @RequestHeader("X-User-Id") String userId) {
        List<ObjectModel> children = syncService.getChildObjectsForUser(parentId, userId);
        return ResponseEntity.ok(children);
    }

    /**
     * GET /api/objects/{id}
     * Получить объект по ID (с проверкой доступа)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ObjectModel> getObjectById(@PathVariable String id,
                                                     @RequestHeader("X-User-Id") String userId) {
        ObjectModel object = syncService.getObjectById(id);
        if (object == null) {
            return ResponseEntity.notFound().build();
        }
        // Проверяем, принадлежит ли объект пользователю
        if (object.getUserId() == null || !object.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(object);
    }

    /**
     * POST /api/objects
     * Создать новый объект
     */
    @PostMapping
    public ResponseEntity<ObjectModel> createObject(@RequestBody ObjectModel object,
                                                    @RequestHeader("X-User-Id") String userId) {
        System.out.println("=== CONTROLLER ===");
        System.out.println("Header X-User-Id: '" + userId + "'");
        System.out.println("Request body object.getUserId(): '" + object.getUserId() + "'");

        ObjectModel created = syncService.createObject(object, userId);

        System.out.println("Response object.getUserId(): '" + created.getUserId() + "'");
        return ResponseEntity.ok(created);
    }

    /**
     * PUT /api/objects/{id}
     * Обновить объект (с проверкой доступа)
     */
    @PutMapping("/{id}")
    public ResponseEntity<ObjectModel> updateObject(@PathVariable String id,
                                                    @RequestBody ObjectModel object,
                                                    @RequestHeader("X-User-Id") String userId) {
        ObjectModel existing = syncService.getObjectById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (existing.getUserId() == null || !existing.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        object.setId(id);
        ObjectModel updated = syncService.updateObject(object, userId);
        return ResponseEntity.ok(updated);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteObject(@PathVariable String id,
                                             @RequestHeader("X-User-Id") String userId) {
        ObjectModel existing = syncService.getObjectById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (existing.getUserId() == null || !existing.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        syncService.deleteObject(id, userId);
        return ResponseEntity.noContent().build();
    }
}