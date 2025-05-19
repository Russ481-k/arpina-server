package cms.swimming.service.impl;

import cms.swimming.domain.Lesson;
import cms.swimming.dto.LessonDto;
import cms.swimming.repository.EnrollRepository;
import cms.swimming.repository.LessonRepository;
import cms.swimming.service.LessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonServiceImpl implements LessonService {

    private final LessonRepository lessonRepository;
    private final EnrollRepository enrollRepository;

    @Override
    public Page<LessonDto> getAllLessons(Pageable pageable) {
        return lessonRepository.findAll(pageable)
                .map(LessonDto::fromEntity);
    }

    @Override
    public Page<LessonDto> getLessonsByStatus(String status, Pageable pageable) {
        Lesson.LessonStatus lessonStatus = Lesson.LessonStatus.valueOf(status);
        return lessonRepository.findByStatus(lessonStatus, pageable)
                .map(LessonDto::fromEntity);
    }

    @Override
    public List<LessonDto> getLessonsByDateRange(LocalDate startDate, LocalDate endDate, String status) {
        Lesson.LessonStatus lessonStatus = Lesson.LessonStatus.valueOf(status);
        return lessonRepository.findByDateRangeAndStatus(startDate, endDate, lessonStatus)
                .stream()
                .map(LessonDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public LessonDto getLessonById(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new EntityNotFoundException("강습을 찾을 수 없습니다. ID: " + lessonId));
        return LessonDto.fromEntity(lesson);
    }

    @Override
    public long countCurrentEnrollments(Long lessonId) {
        return lessonRepository.countCurrentEnrollments(lessonId);
    }

    @Override
    public long countLockersByGender(Long lessonId, String gender) {
        return enrollRepository.countLockersByGender(lessonId, gender);
    }
} 