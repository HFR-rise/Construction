package com.example.estimateserver.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    private String userId;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private Date connectedAt;

    private Date lastPongAt;

    @Column(nullable = false)
    private boolean active = true;

    public UserSession() {}

    public UserSession(String userId, String sessionId, String deviceId) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.connectedAt = new Date();
        this.lastPongAt = new Date();
        this.active = true;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Date getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Date connectedAt) { this.connectedAt = connectedAt; }

    public Date getLastPongAt() { return lastPongAt; }
    public void setLastPongAt(Date lastPongAt) { this.lastPongAt = lastPongAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}