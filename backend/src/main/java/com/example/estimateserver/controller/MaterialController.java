package com.example.estimateserver.controller;

import com.example.estimateserver.model.Material;
import com.example.estimateserver.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materials")
@CrossOrigin(origins = "*")
public class MaterialController {

    private final SyncService syncService;

    public MaterialController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    public ResponseEntity<Material> addMaterial(@RequestBody Material material,
                                                @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(syncService.addMaterial(material, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Material> updateMaterial(@PathVariable String id,
                                                   @RequestBody Material material,
                                                   @RequestHeader("X-User-Id") String userId) {
        material.setId(id);
        return ResponseEntity.ok(syncService.updateMaterial(material, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMaterial(@PathVariable String id,
                                               @RequestHeader("X-User-Id") String userId) {
        syncService.deleteMaterial(id, userId);
        return ResponseEntity.ok().build();
    }
}