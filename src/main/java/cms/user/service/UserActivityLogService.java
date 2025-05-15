package cms.user.service;

import java.time.LocalDateTime;
import java.util.List;

import cms.user.domain.UserActivityLog;
import cms.user.dto.UserActivityLogDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserActivityLogService {
    /**
     * 사용자 활동 로그를 기록합니다.
     * @param uuid 로그 엔트리의 UUID
     * @param userUuid 사용자 UUID
     * @param groupId 그룹 UUID
     * @param organizationId 조직 UUID
     * @param action 활동 유형
     * @param description 활동 설명
     * @param userAgent 사용자 에이전트
     * @param createdBy 생성자 UUID
     * @param createdIp 생성자 IP
     */
    void logActivity(String uuid, String userUuid, String groupId, String organizationId, String action, String description, 
                    String userAgent, String createdBy, String createdIp);

    /**
     * 특정 사용자의 활동 로그를 조회합니다.
     * @param uuid 사용자 UUID
     * @return 활동 로그 목록
     */
    List<UserActivityLog> getUserActivities(String uuid);

    /**
     * 특정 기간 동안의 사용자 활동 로그를 조회합니다.
     * @param uuid 사용자 UUID
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 활동 로그 목록
     */
    List<UserActivityLog> getUserActivitiesByDateRange(String uuid, LocalDateTime startDate, LocalDateTime endDate);

    List<UserActivityLog> getUserActivityLogs(String uuid, LocalDateTime startDate, LocalDateTime endDate);

    UserActivityLog getUserActivityLog(String uuid, String logId);

    Page<UserActivityLogDto> getActivityLogs(Pageable pageable);
    Page<UserActivityLogDto> getActivityLogsByUser(String uuid, Pageable pageable);
    UserActivityLogDto getActivityLog(String logId);
    void deleteActivityLog(String logId);
} 