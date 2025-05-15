package cms.scheduler;

import cms.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final FileService fileService;

    // 매월 첫째 주 금요일 오전 3시에 실행
    // Cron 표현식: 초 분 시 일 월 요일 (년도는 생략 가능)
    // FRI#1 : 해당 월의 첫 번째 금요일
    @Scheduled(cron = "0 0 3 ? * FRI#1")
    public void cleanupOrphanedFiles() {
        log.info("Starting scheduled orphaned file cleanup task...");

        // BbsArticleServiceImpl.EDITOR_EMBEDDED_MEDIA 와 일치해야 함
        // BbsArticleServiceImpl.ARTICLE_ATTACHMENT_MENU_TYPE 와 일치해야 함
        List<String> targetMenuTypes = Arrays.asList(
                "ARTICLE_ATTACHMENT", 
                "EDITOR_EMBEDDED_MEDIA"
        );

        try {
            // FileServiceImpl에 구현된 메소드 호출
            int deletedCount = fileService.deleteOrphanedFilesByMissingArticle(targetMenuTypes);
            log.info("Successfully deleted {} orphaned files.", deletedCount);
        } catch (Exception e) {
            // 스케줄러 실행 중 발생하는 모든 예외를 로깅하여 스케줄러 스레드가 죽지 않도록 함
            log.error("Error during scheduled orphaned file cleanup task. Task will continue on next schedule.", e);
        }

        log.info("Finished scheduled orphaned file cleanup task.");
    }
} 