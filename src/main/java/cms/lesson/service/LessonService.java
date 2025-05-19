package cms.lesson.service;

import cms.lesson.domain.Lesson;
import java.util.Optional;

public interface LessonService {
    Optional<Lesson> findById(Long lessonId);
    // Add other service methods as needed, e.g., for searching lessons, creating, updating, etc.
} 