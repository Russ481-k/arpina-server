package cms.lesson.service;

import cms.lesson.domain.Lesson;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service("mockLessonService") // Qualify this as the mock implementation
public class MockLessonServiceImpl implements LessonService {

    private final Map<Long, Lesson> mockLessons = new HashMap<>();

    public MockLessonServiceImpl() {
        // Initialize with some mock data
        mockLessons.put(1L, Lesson.builder()
                .id(1L)
                .name("기초 수영 강좌")
                .description("물을 무서워하는 분들을 위한 기초반입니다.")
                .price(new BigDecimal("50000"))
                .period("1개월")
                .time("월수금 10:00-10:50")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        mockLessons.put(2L, Lesson.builder()
                .id(2L)
                .name("중급 요가 클래스")
                .description("주 3회 진행되는 중급자 대상 요가 수업.")
                .price(new BigDecimal("75000"))
                .period("3개월")
                .time("화목 19:00-20:00, 토 11:00-12:00")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    @Override
    public Optional<Lesson> findById(Long lessonId) {
        return Optional.ofNullable(mockLessons.get(lessonId));
    }

    // Implement other methods from LessonService as needed
} 