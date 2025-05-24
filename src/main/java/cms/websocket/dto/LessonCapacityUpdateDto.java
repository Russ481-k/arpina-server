package cms.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonCapacityUpdateDto {
    private Long lessonId;
    private String type; // "connected", "subscribed", "capacity_update", "lesson_closed" 등
    private int capacity;
    private int paidEnrollments;
    private int unpaidEnrollments;
    private int availableSlots;
    private long timestamp;

    // 간편 생성자
    public LessonCapacityUpdateDto(Long lessonId, String type, int capacity, int paidEnrollments, int unpaidEnrollments) {
        this.lessonId = lessonId;
        this.type = type;
        this.capacity = capacity;
        this.paidEnrollments = paidEnrollments;
        this.unpaidEnrollments = unpaidEnrollments;
        this.availableSlots = capacity - paidEnrollments - unpaidEnrollments;
        this.timestamp = System.currentTimeMillis();
    }
} 