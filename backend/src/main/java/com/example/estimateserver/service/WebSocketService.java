package com.example.estimateserver.service;

import com.example.estimateserver.dto.SyncMessage;
import com.example.estimateserver.model.UserSession;
import com.example.estimateserver.repository.UserSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class WebSocketService {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final Map<String, List<SyncMessage>> offlineMessages = new ConcurrentHashMap<>();

    private final Map<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserSessionRepository sessionRepository;

    private final AtomicBoolean serverJustStarted = new AtomicBoolean(true);

    private volatile boolean acceptingNewConnections = false;

    private final Set<String> forceLogoutSentOnStartup = ConcurrentHashMap.newKeySet();

    private final Set<String> pendingSessionClosure = ConcurrentHashMap.newKeySet();

    public WebSocketService(UserSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * ✅ ПОТОКОБЕЗОПАСНОЕ добавление сессии с использованием блокировки на userId
     */
    public void addSession(String userId, WebSocketSession session) {
        System.out.println("🔵 ADD SESSION for user: " + userId + ", sessionId: " + session.getId());

        if (userId == null || session == null) {
            System.err.println("Cannot add session: userId or session is null");
            return;
        }

        if (serverJustStarted.get() && !acceptingNewConnections) {
            System.out.println("⏸️ Server still in startup phase, rejecting connection for user: " + userId);
            try {
                sendForceLogoutToSession(userId, session);
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                System.err.println("Error rejecting connection: " + e.getMessage());
            }
            return;
        }

        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());

        lock.lock();
        try {
            WebSocketSession existingSession = sessions.get(userId);

            if (existingSession != null && existingSession.isOpen()) {
                System.out.println("⚠️ Replacing existing session for user: " + userId);
                System.out.println("   Old session ID: " + existingSession.getId());
                System.out.println("   New session ID: " + session.getId());

                sendForceLogoutToSession(userId, existingSession);

                try {
                    existingSession.close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException e) {
                    System.err.println("Error closing old session: " + e.getMessage());
                }

                sessions.remove(userId);
                sessionToUser.remove(existingSession.getId());
            }

            sessions.put(userId, session);
            sessionToUser.put(session.getId(), userId);

            String deviceId = getDeviceIdFromSession(session);
            saveSessionToDatabase(userId, session.getId(), deviceId);

            List<SyncMessage> pendingMessages = offlineMessages.remove(userId);
            if (pendingMessages != null && !pendingMessages.isEmpty()) {
                System.out.println("📬 Sending " + pendingMessages.size() + " pending messages to user " + userId);
                for (SyncMessage message : pendingMessages) {
                    sendToUser(userId, message);
                }
            }

            System.out.println("✅ User " + userId + " connected. Total sessions: " + sessions.size());

        } finally {
            lock.unlock();
        }
    }

    /**
     * ✅ ПОТОКОБЕЗОПАСНОЕ удаление сессии
     */
    public void removeSession(String userId, boolean isServerShutdown) {
        System.out.println("🔴 REMOVE SESSION for user: " + userId + ", isServerShutdown: " + isServerShutdown);

        ReentrantLock lock = userLocks.get(userId);
        if (lock != null) {
            lock.lock();
            try {
                WebSocketSession session = sessions.remove(userId);
                if (session != null) {
                    sessionToUser.remove(session.getId());
                    System.out.println("🗑️ Removed session for user: " + userId);
                }

                if (!isServerShutdown) {
                    sessionRepository.deactivateUserSession(userId);
                    System.out.println("💾 Deactivated session in database for user: " + userId);
                }
            } finally {
                lock.unlock();
            }
        } else {
            WebSocketSession session = sessions.remove(userId);
            if (session != null) {
                sessionToUser.remove(session.getId());
            }
            if (!isServerShutdown) {
                sessionRepository.deactivateUserSession(userId);
            }
        }

        if (!sessions.containsKey(userId)) {
            userLocks.remove(userId);
        }
    }


    public void removeSession(String userId) {
        removeSession(userId, false);
    }

    /**
     * ✅ ПОТОКОБЕЗОПАСНАЯ отправка сообщения пользователю
     */
    public void sendToUser(String userId, SyncMessage message) {
        if (serverJustStarted.get() && !"FORCE_LOGOUT".equals(message.getType())) {
            System.out.println("📦 Server starting, queueing message for user " + userId);
            saveOfflineMessage(userId, message);
            return;
        }

        WebSocketSession session = sessions.get(userId);

        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
                System.out.println("📤 Sent to user " + userId + ": " + message.getType());
            } catch (IOException e) {
                System.err.println("Error sending to user " + userId + ": " + e.getMessage());
                saveOfflineMessage(userId, message);
            }
        } else {
            saveOfflineMessage(userId, message);
        }
    }

    /**
     * ✅ Проверка наличия сессии (read-only, без блокировки)
     */
    public boolean hasSession(String userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * ✅ Получение сессии (read-only)
     */
    public WebSocketSession getSession(String userId) {
        return sessions.get(userId);
    }

    /**
     * ✅ Получение всех онлайн пользователей (копия для безопасности)
     */
    public Set<String> getOnlineUsers() {
        return new HashSet<>(sessions.keySet());
    }

    /**
     * ✅ Потокобезопасная установка флага запуска сервера
     */
    public void setServerJustStarted(boolean started) {
        System.out.println("🔄 Setting serverJustStarted to: " + started);
        this.serverJustStarted.set(started);

        if (!started) {
            forceLogoutSentOnStartup.clear();
            pendingSessionClosure.clear();

            acceptingNewConnections = true;
        } else {
            acceptingNewConnections = false;
        }
    }

    public boolean isServerJustStarted() {
        return serverJustStarted.get();
    }


    public void forceLogoutAllActiveSessionsImmediately() {
        System.out.println("⚠️ FORCE LOGOUT ALL ACTIVE SESSIONS");

        acceptingNewConnections = false;

        Set<String> onlineUsers = new HashSet<>(sessions.keySet());

        int sentCount = 0;

        for (String userId : onlineUsers) {
            ReentrantLock lock = userLocks.get(userId);
            if (lock != null) {
                lock.lock();
                try {
                    WebSocketSession session = sessions.get(userId);
                    if (session != null && session.isOpen()) {
                        if (sendForceLogoutToSession(userId, session)) {
                            sentCount++;
                            forceLogoutSentOnStartup.add(userId);
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

        System.out.println("✅ Sent FORCE_LOGOUT to " + sentCount + " users");

        scheduleCloseSessionsAfterDelay(3000);
    }

    /**
     * ✅ Периодическая очистка старых блокировок (чтобы не было утечек)
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanupStaleLocks() {
        for (Map.Entry<String, ReentrantLock> entry : userLocks.entrySet()) {
            String userId = entry.getKey();
            ReentrantLock lock = entry.getValue();

            if (!sessions.containsKey(userId) && !lock.isLocked()) {
                userLocks.remove(userId);
                System.out.println("🧹 Cleaned up stale lock for user: " + userId);
            }
        }
    }

    /**
     * ✅ Потокобезопасная отправка PING всем клиентам
     */
    @Scheduled(fixedDelay = 30000)
    public void sendPingToAllClients() {
        if (serverJustStarted.get()) {
            System.out.println("⏸️ Skipping ping during startup phase");
            return;
        }

        Set<String> onlineUsers = new HashSet<>(sessions.keySet());

        for (String userId : onlineUsers) {
            WebSocketSession session = sessions.get(userId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage("ping"));
                    System.out.println("🏓 Sent ping to user: " + userId);
                } catch (IOException e) {
                    System.err.println("Failed to send ping to user: " + userId);
                    scheduleSessionClose(userId, session);
                }
            }
        }
    }


    private boolean sendForceLogoutToSession(String userId, WebSocketSession session) {
        try {
            SyncMessage message = new SyncMessage(
                    "FORCE_LOGOUT", "SESSION", null, null,
                    userId, new Date(), null
            );
            String json = objectMapper.writeValueAsString(message);

            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }

            System.out.println("📤 Sent FORCE_LOGOUT to user: " + userId);
            return true;

        } catch (IOException e) {
            System.err.println("Failed to send FORCE_LOGOUT: " + e.getMessage());
            return false;
        }
    }

    private void saveOfflineMessage(String userId, SyncMessage message) {
        offlineMessages.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(message);

        List<SyncMessage> userMessages = offlineMessages.get(userId);
        while (userMessages != null && userMessages.size() > 50) {
            userMessages.remove(0);
        }
    }

    private void saveSessionToDatabase(String userId, String sessionId, String deviceId) {
        try {
            Optional<UserSession> existing = sessionRepository.findByUserId(userId);
            if (existing.isPresent()) {
                UserSession session = existing.get();
                session.setSessionId(sessionId);
                session.setDeviceId(deviceId);
                session.setActive(true);
                session.setConnectedAt(new Date());
                session.setLastPongAt(new Date());
                sessionRepository.save(session);
            } else {
                UserSession userSession = new UserSession(userId, sessionId, deviceId);
                sessionRepository.save(userSession);
            }
        } catch (Exception e) {
            System.err.println("Failed to save session to database: " + e.getMessage());
        }
    }

    private String getDeviceIdFromSession(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("deviceId=")) {
                        return param.substring(9);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting deviceId: " + e.getMessage());
        }
        return UUID.randomUUID().toString();
    }

    private void scheduleSessionClose(String userId, WebSocketSession session) {
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            WebSocketSession currentSession = sessions.get(userId);
            if (currentSession == session && session.isOpen()) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                    System.out.println("Closed unresponsive session for user: " + userId);
                } catch (IOException e) {
                    System.err.println("Error closing session: " + e.getMessage());
                }
                sessions.remove(userId);
                sessionToUser.remove(session.getId());
            }
        }, 5000, TimeUnit.MILLISECONDS);
    }

    private void scheduleCloseSessionsAfterDelay(long delayMs) {
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            System.out.println("🔒 Closing all sessions after force logout...");

            Set<String> onlineUsers = new HashSet<>(sessions.keySet());

            for (String userId : onlineUsers) {
                ReentrantLock lock = userLocks.get(userId);
                if (lock != null) {
                    lock.lock();
                    try {
                        WebSocketSession session = sessions.get(userId);
                        if (session != null && session.isOpen()) {
                            pendingSessionClosure.add(userId);
                            session.close(CloseStatus.POLICY_VIOLATION);
                        }
                    } catch (IOException e) {
                        System.err.println("Error closing session: " + e.getMessage());
                    } finally {
                        pendingSessionClosure.remove(userId);
                        lock.unlock();
                    }
                }
            }

            sessions.clear();
            sessionToUser.clear();

            acceptingNewConnections = true;

            Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                serverJustStarted.set(false);
                forceLogoutSentOnStartup.clear();
                System.out.println("✅ Server startup phase completed");
            }, 5000, TimeUnit.MILLISECONDS);

        }, delayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void gracefulShutdown() {
        System.out.println("=== SERVER SHUTTING DOWN ===");

        Set<String> onlineUsers = new HashSet<>(sessions.keySet());

        for (String userId : onlineUsers) {
            ReentrantLock lock = userLocks.get(userId);
            if (lock != null) {
                lock.lock();
                try {
                    WebSocketSession session = sessions.get(userId);
                    if (session != null && session.isOpen()) {
                        session.close(CloseStatus.NORMAL);
                    }
                } catch (IOException e) {
                    System.err.println("Error closing session: " + e.getMessage());
                } finally {
                    lock.unlock();
                }
            }
        }

        sessions.clear();
        sessionToUser.clear();
        offlineMessages.clear();
        userLocks.clear();

        System.out.println("All sessions closed, database sessions preserved");
    }


    public void updateSessionLastPong(String userId) {
        try {
            Optional<UserSession> sessionOpt = sessionRepository.findByUserId(userId);
            if (sessionOpt.isPresent()) {
                UserSession session = sessionOpt.get();
                session.setLastPongAt(new Date());
                sessionRepository.save(session);
            }
        } catch (Exception e) {
            System.err.println("Error updating last pong: " + e.getMessage());
        }
    }



    public void queueForceLogoutForOfflineUser(String userId) {
        SyncMessage forceLogoutMessage = new SyncMessage(
                "FORCE_LOGOUT", "SESSION", null, null,
                userId, new Date(), null
        );
        saveOfflineMessage(userId, forceLogoutMessage);
    }

    public void sendForceLogout(String userId) {
        ReentrantLock lock = userLocks.get(userId);
        if (lock != null) {
            lock.lock();
            try {
                WebSocketSession session = sessions.get(userId);
                if (session != null && session.isOpen()) {
                    sendForceLogoutToSession(userId, session);
                    session.close(CloseStatus.POLICY_VIOLATION);
                }
            } catch (IOException e) {
                System.err.println("Error closing session: " + e.getMessage());
            } finally {
                lock.unlock();
            }
        }
        removeSession(userId, false);
    }

    public boolean hasAnySession(String userId) {
        return sessions.containsKey(userId);
    }

    public void dumpSessionsState() {
        System.out.println("========== SESSIONS STATE ==========");
        System.out.println("Total sessions: " + sessions.size());
        System.out.println("Server just started: " + serverJustStarted.get());
        System.out.println("Accepting connections: " + acceptingNewConnections);
        for (String userId : sessions.keySet()) {
            WebSocketSession session = sessions.get(userId);
            System.out.println("   User: " + userId + ", Session: " +
                    (session != null ? session.getId() : "null") + ", Open: " +
                    (session != null && session.isOpen()));
        }
        System.out.println("====================================");
    }

    public int getActiveSessionsCount() {
        return sessions.size();
    }
}
