package cms.websocket.config;

import cms.websocket.handler.LessonCapacityWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private LessonCapacityWebSocketHandler lessonCapacityHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 실시간 강좌 정원 정보 업데이트
        registry.addHandler(lessonCapacityHandler, "/ws/lesson-capacity")
                // .setAllowedOrigins("*") // Nginx에서 처리하도록 주석 처리
                .withSockJS(); // SockJS fallback 지원
    }
} 