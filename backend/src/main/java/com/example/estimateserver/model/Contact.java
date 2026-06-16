package com.example.estimateserver.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "contacts")
public class Contact {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String name;

    private String description;
    private Long createdAt = System.currentTimeMillis();

    public Contact() {}

    @Column(nullable = false)
    private String userId;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}