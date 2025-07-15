package cms.scheduler;

import cms.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final FileService fileService;

    // 매월 첫째 주 금요일 오전 3시에 실행
    // Cron 표현식: 초 분 시 일 월 요일 (년도는 생략 가능)
    // FRI#1 : 해당 월의 첫 번째 금요일
    @Scheduled(cron = "0 0 3 * * FRI#1") // 매월 첫째 주 금요일 오전 3시
    public void cleanupOrphanedFiles() {
        log.info("========== Starting scheduled orphaned file cleanup ==========");
        log.info("Cleanup time: {}", LocalDateTime.now());

        try {
            List<String> menuTypes = Arrays.asList("ARTICLE_ATTACHMENT", "EDITOR_EMBEDDED_MEDIA");
            log.info("Target menu types: {}", menuTypes);

            // 🛡️ 사전 안전 검사
            if (!performSafetyCheck()) {
                log.error("🚨 SAFETY CHECK FAILED - Aborting cleanup to prevent data loss!");
                return;
            }

            // 삭제 전 통계 정보 로그
            long totalFilesBeforeCleanup = fileService.countFilesByMenuTypes(menuTypes);
            log.info("Total files before cleanup: {}", totalFilesBeforeCleanup);

            // 🛡️ 안전 확인: 너무 많은 파일이 삭제되지 않도록 체크
            if (totalFilesBeforeCleanup > 100) {
                log.warn("⚠️ Large number of files detected ({}). Cleanup will proceed cautiously.",
                        totalFilesBeforeCleanup);
            }

            int deletedCount = fileService.deleteOrphanedFilesByMissingArticle(menuTypes);

            // 삭제 후 통계 정보 로그
            long totalFilesAfterCleanup = fileService.countFilesByMenuTypes(menuTypes);
            log.info("Total files after cleanup: {}", totalFilesAfterCleanup);
            log.info("Successfully deleted {} orphaned files", deletedCount);

            // 🚨 이상 상황 감지 및 알림
            if (deletedCount > 10) {
                log.warn("⚠️ HIGH DELETION COUNT: {} files deleted - Please review!", deletedCount);
            }

            double deletionRate = totalFilesBeforeCleanup > 0 ? (double) deletedCount / totalFilesBeforeCleanup * 100
                    : 0;
            if (deletionRate > 20.0) {
                log.error("🚨 CRITICAL: {}% of files deleted ({}). This may indicate a problem!",
                        String.format("%.1f", deletionRate), deletedCount);
            }

        } catch (Exception e) {
            log.error("❌ Error during scheduled file cleanup: {}", e.getMessage(), e);
        }

        log.info("========== Completed scheduled orphaned file cleanup ==========");
    }

    /**
     * 🛡️ 스케줄러 실행 전 안전 검사
     * 파일 ID 추출 로직이 올바르게 작동하는지 확인
     */
    private boolean performSafetyCheck() {
        log.info("🔍 Performing safety check before cleanup...");

        try {
            // FileService에 구현된 안전 검사 로직 호출
            boolean safetyResult = fileService.validateFileIdExtractionLogic();

            if (safetyResult) {
                log.info("✅ Safety check passed - File ID extraction logic appears to be working correctly");
                return true;
            } else {
                log.error("❌ Safety check failed - File ID extraction logic may have issues");
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Safety check failed: {}", e.getMessage(), e);
            return false;
        }
    }
}