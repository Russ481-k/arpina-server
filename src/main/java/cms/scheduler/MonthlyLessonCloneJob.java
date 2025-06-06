package cms.scheduler;

import cms.swimming.domain.Lesson;
import cms.swimming.repository.LessonRepository;
import cms.swimming.repository.specification.LessonSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyLessonCloneJob {

    private final LessonRepository lessonRepository;

    @Scheduled(cron = "0 0 15 6 * ?")
    @Transactional
    public void cloneMonthlyLessons() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth nextMonth = currentMonth.plusMonths(1);
        log.info("Starting monthly lesson clone job for {}.", nextMonth);

        List<Lesson> lessonsToClone = lessonRepository
                .findAll(LessonSpecification.filterBy(currentMonth.getYear(), currentMonth.getMonthValue()));

        if (lessonsToClone.isEmpty()) {
            log.info("No lessons found for {} to clone.", currentMonth);
            return;
        }

        log.info("Found {} lessons from {} to clone for {}.", lessonsToClone.size(), currentMonth, nextMonth);

        for (Lesson originalLesson : lessonsToClone) {
            Lesson clonedLesson = Lesson.builder()
                    .title(originalLesson.getTitle())
                    .displayName(originalLesson.getDisplayName())
                    .startDate(originalLesson.getStartDate().plusMonths(1))
                    .endDate(originalLesson.getEndDate().plusMonths(1))
                    .capacity(originalLesson.getCapacity())
                    .price(originalLesson.getPrice())
                    .instructorName(originalLesson.getInstructorName())
                    .lessonTime(originalLesson.getLessonTime())
                    .locationName(originalLesson.getLocationName())
                    .registrationStartDateTime(originalLesson.getRegistrationStartDateTime() != null
                            ? originalLesson.getRegistrationStartDateTime().plusMonths(1)
                            : null)
                    .registrationEndDateTime(originalLesson.getRegistrationEndDateTime().plusMonths(1))
                    .createdBy("SYSTEM_SCHEDULER")
                    .createdIp("127.0.0.1")
                    .build();

            lessonRepository.save(clonedLesson);
            log.debug("Cloned lesson ID {} to new lesson for next month.", originalLesson.getLessonId());
        }

        log.info("Successfully cloned {} lessons for {}.", lessonsToClone.size(), nextMonth);
    }
}