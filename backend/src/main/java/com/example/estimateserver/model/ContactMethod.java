package com.example.estimateserver.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "contact_methods")
public class ContactMethod {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String contactId;

    @Column(nullable = false)
    private String methodType;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private String userId;

    public ContactMethod() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContactId() { return contactId; }
    public void setContactId(String contactId) { this.contactId = contactId; }

    public String getMethodType() { return methodType; }
    public void setMethodType(String methodType) { this.methodType = methodType; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}