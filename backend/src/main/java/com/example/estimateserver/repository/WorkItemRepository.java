package com.example.estimateserver.repository;

import com.example.estimateserver.model.WorkItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, String> {
    List<WorkItem> findByProjectId(String projectId);
}