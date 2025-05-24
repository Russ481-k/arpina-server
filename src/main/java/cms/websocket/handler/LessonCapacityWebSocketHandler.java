package cms.websocket.handler;

import cms.websocket.dto.LessonCapacityUpdateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class LessonCapacityWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(LessonCapacityWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 레슨별 구독자 관리
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> lessonSubscribers = new ConcurrentHashMap<>();
    // 전체 활성 세션 관리
    private final CopyOnWriteArraySet<WebSocketSession> allSessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        allSessions.add(session);
        logger.info("[WebSocket] New connection established: {}", session.getId());
        
        // 연결 확인 메시지 전송
        sendMessage(session, new LessonCapacityUpdateDto(null, "connected", 0, 0, 0));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        try {
            String payload = message.getPayload().toString();
            logger.debug("[WebSocket] Message received from {}: {}", session.getId(), payload);
            
            // 클라이언트에서 특정 레슨 구독 요청 처리
            if (payload.startsWith("subscribe:")) {
                Long lessonId = Long.parseLong(payload.substring(10));
                subscribeLessonUpdates(session, lessonId);
            } else if (payload.startsWith("unsubscribe:")) {
                Long lessonId = Long.parseLong(payload.substring(12));
                unsubscribeLessonUpdates(session, lessonId);
            }
        } catch (Exception e) {
            logger.error("[WebSocket] Error handling message from {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("[WebSocket] Transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        allSessions.remove(session);
        
        // 모든 레슨 구독에서 제거
        lessonSubscribers.values().forEach(subscribers -> subscribers.remove(session));
        
        logger.info("[WebSocket] Connection closed: {}, reason: {}", session.getId(), closeStatus.getReason());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 특정 레슨의 정원 정보 업데이트를 구독자들에게 브로드캐스트
     */
    public void broadcastLessonCapacityUpdate(Long lessonId, int capacity, int paidEnrollments, int unpaidEnrollments) {
        CopyOnWriteArraySet<WebSocketSession> subscribers = lessonSubscribers.get(lessonId);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        LessonCapacityUpdateDto updateDto = LessonCapacityUpdateDto.builder()
                .lessonId(lessonId)
                .type("capacity_update")
                .capacity(capacity)
                .paidEnrollments(paidEnrollments)
                .unpaidEnrollments(unpaidEnrollments)
                .availableSlots(capacity - paidEnrollments - unpaidEnrollments)
                .build();

        subscribers.forEach(session -> {
            if (session.isOpen()) {
                sendMessage(session, updateDto);
            } else {
                subscribers.remove(session);
            }
        });

        logger.info("[WebSocket] Broadcasted capacity update for lesson {} to {} subscribers", 
                   lessonId, subscribers.size());
    }

    /**
     * 특정 레슨 업데이트 구독
     */
    private void subscribeLessonUpdates(WebSocketSession session, Long lessonId) {
        lessonSubscribers.computeIfAbsent(lessonId, k -> new CopyOnWriteArraySet<>()).add(session);
        logger.info("[WebSocket] Session {} subscribed to lesson {}", session.getId(), lessonId);
        
        // 구독 확인 메시지 전송
        sendMessage(session, new LessonCapacityUpdateDto(lessonId, "subscribed", 0, 0, 0));
    }

    /**
     * 특정 레슨 업데이트 구독 해제
     */
    private void unsubscribeLessonUpdates(WebSocketSession session, Long lessonId) {
        CopyOnWriteArraySet<WebSocketSession> subscribers = lessonSubscribers.get(lessonId);
        if (subscribers != null) {
            subscribers.remove(session);
            if (subscribers.isEmpty()) {
                lessonSubscribers.remove(lessonId);
            }
        }
        logger.info("[WebSocket] Session {} unsubscribed from lesson {}", session.getId(), lessonId);
    }

    /**
     * 메시지 전송 헬퍼 메소드
     */
    private void sendMessage(WebSocketSession session, LessonCapacityUpdateDto message) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            logger.error("[WebSocket] Failed to send message to session {}: {}", session.getId(), e.getMessage());
        }
    }
} 