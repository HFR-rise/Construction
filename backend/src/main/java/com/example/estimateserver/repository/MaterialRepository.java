package com.example.estimateserver.repository;

import com.example.estimateserver.model.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, String> {
    List<Material> findByProjectId(String projectId);

    List<Material> findByUserId(String userId);

    @Query("SELECT m FROM Material m WHERE m.projectId IN :projectIds")
    List<Material> findByProjectIds(@Param("projectIds") List<String> projectIds);
}