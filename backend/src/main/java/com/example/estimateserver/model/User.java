package com.example.estimateserver.model;

import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    private String name;
    private String verificationCode;
    private Date codeExpiresAt;
    private boolean isVerified = false;

    private Date createdAt = new Date();
    private Date lastActiveAt = new Date();

    @Column(name = "user_id")
    private String userId;

    @Column(name = "active_session_id", nullable = true)
    private String activeSessionId;

    @Column(name = "current_device_info", nullable = true)
    private String currentDeviceInfo;

    @Column(name = "last_activity_at", nullable = true)
    private Date lastActivityAt;

    public User() {}

    public User(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.userId = this.id;
    }

    // ===== ГЕТТЕРЫ И СЕТТЕРЫ =====
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }

    public Date getCodeExpiresAt() { return codeExpiresAt; }
    public void setCodeExpiresAt(Date codeExpiresAt) { this.codeExpiresAt = codeExpiresAt; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Date lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getActiveSessionId() { return activeSessionId; }
    public void setActiveSessionId(String activeSessionId) { this.activeSessionId = activeSessionId; }

    public String getCurrentDeviceInfo() { return currentDeviceInfo; }
    public void setCurrentDeviceInfo(String currentDeviceInfo) { this.currentDeviceInfo = currentDeviceInfo; }

    public Date getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Date lastActivityAt) { this.lastActivityAt = lastActivityAt; }
}