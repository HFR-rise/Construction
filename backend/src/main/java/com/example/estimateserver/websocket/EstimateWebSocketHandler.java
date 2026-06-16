package com.example.estimateserver.websocket;

import com.example.estimateserver.service.UserService;
import com.example.estimateserver.service.WebSocketService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.net.URI;
import java.util.logging.Logger;

@Component
public class EstimateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = Logger.getLogger(EstimateWebSocketHandler.class.getName());
    private final WebSocketService webSocketService;
    private final UserService userService;

    public EstimateWebSocketHandler(WebSocketService webSocketService, UserService userService) {
        this.webSocketService = webSocketService;
        this.userService = userService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractParameter(session, "userId");
        String deviceId = extractParameter(session, "deviceId");

        System.out.println("🔵 WebSocket connection attempt - User: " + userId + ", Device: " + deviceId);

        if (userId == null || userId.isEmpty()) {
            logger.warning("❌ Connection rejected: userId not provided");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        if (deviceId == null || deviceId.isEmpty()) {
            logger.warning("❌ Connection rejected: deviceId not provided");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        if (webSocketService.isServerJustStarted() && !isAcceptingConnections()) {
            logger.warning("❌ Connection rejected: server in startup phase");
            session.sendMessage(new TextMessage("{\"type\":\"FORCE_LOGOUT\",\"reason\":\"server_startup\"}"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        boolean isValidSession = userService.isSessionValid(userId, deviceId);

        if (!isValidSession) {
            logger.warning("❌ Connection rejected: invalid session for user " + userId);
            session.sendMessage(new TextMessage("{\"type\":\"FORCE_LOGOUT\",\"reason\":\"session_invalid\"}"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        webSocketService.addSession(userId, session);
        logger.info("✅ WebSocket connected for user: " + userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = extractParameter(session, "userId");
        String deviceId = extractParameter(session, "deviceId");

        logger.info("🔴 WebSocket closed - User: " + userId + ", Code: " + status.getCode());

        if (userId != null && !userId.isEmpty()) {
            boolean isServerShutdown = (status.getCode() == 1001) ||
                    ("Service shutdown".equals(status.getReason()));
            webSocketService.removeSession(userId, isServerShutdown);
        }

        webSocketService.dumpSessionsState();
        super.afterConnectionClosed(session, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String userId = extractParameter(session, "userId");
        String deviceId = extractParameter(session, "deviceId");

        if (userId != null && deviceId != null) {
            if (!userService.isSessionValid(userId, deviceId)) {
                logger.warning("⚠️ Invalid session for user " + userId + ", closing");
                session.sendMessage(new TextMessage("{\"type\":\"FORCE_LOGOUT\",\"reason\":\"session_expired\"}"));
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
        }

        if ("ping".equalsIgnoreCase(payload)) {
            session.sendMessage(new TextMessage("pong"));
            return;
        }

        if ("pong".equalsIgnoreCase(payload)) {
            webSocketService.updateSessionLastPong(userId);
            return;
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = extractParameter(session, "userId");
        logger.severe("🔴 Transport error for user " + userId + ": " + exception.getMessage());

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private String extractParameter(WebSocketSession session, String paramName) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) return null;

            for (String pair : uri.getQuery().split("&")) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2 && paramName.equals(keyValue[0])) {
                    return keyValue[1];
                }
            }
            return null;
        } catch (Exception e) {
            logger.severe("Error extracting parameter: " + e.getMessage());
            return null;
        }
    }

    private boolean isAcceptingConnections() {
        return !webSocketService.isServerJustStarted();
    }
}