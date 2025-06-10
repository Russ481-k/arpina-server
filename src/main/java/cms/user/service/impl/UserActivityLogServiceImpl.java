package cms.user.service.impl;

import cms.user.domain.UserActivityLog;
import cms.user.dto.UserActivityLogDto;
import cms.user.repository.UserActivityLogRepository;
import cms.user.repository.UserRepository;
import cms.user.service.UserActivityLogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import cms.user.domain.User;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserActivityLogServiceImpl implements UserActivityLogService {

    private final UserActivityLogRepository userActivityLogRepository;
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(UserActivityLogServiceImpl.class);

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(String uuid, String userUuid, String groupId, String organizationId, String action,
            String description,
            String userAgent, String createdBy, String createdIp) {

        String effectiveOrganizationId = organizationId;
        boolean isValidOrgId = false;

        if (StringUtils.hasText(effectiveOrganizationId)) {
            try {
                // UUID 형식인지 확인. "0000..." 같은 값도 유효한 UUID 형식이지만,
                // 실제 DB에 존재하는지 확인하는 것이 가장 정확하지만 비용이 크므로
                // 여기서는 형식 검사와 함께 기본적인 null-safe 체크를 강화합니다.
                UUID.fromString(effectiveOrganizationId);
                // "0000..." 같은 값은 형식상 유효하므로, 아래 로직에서 user 정보로 덮어쓰도록 유도합니다.
                if (!"00000000-0000-0000-0000-000000000000".equals(effectiveOrganizationId)) {
                    isValidOrgId = true;
                }
            } catch (IllegalArgumentException e) {
                // UUID 형식이 아님
                isValidOrgId = false;
            }
        }

        // 1. organizationId가 유효하지 않은 경우, userUuid를 통해 다시 조회
        if (!isValidOrgId) {
            if (StringUtils.hasText(userUuid)) {
                User user = userRepository.findById(userUuid).orElse(null);
                if (user != null && StringUtils.hasText(user.getOrganizationId())) {
                    effectiveOrganizationId = user.getOrganizationId();
                    log.debug("organizationId가 유효하지 않거나 누락되어 사용자의 정보로 대체합니다. userUuid: {}, new organizationId: {}",
                            userUuid,
                            effectiveOrganizationId);
                } else {
                    // 사용자를 찾을 수 없거나 사용자에게도 organizationId가 없는 경우, 로그 기록을 건너뜀
                    log.warn("활동 로그 기록 실패: organizationId를 확인할 수 없습니다. userUuid: {}", userUuid);
                    return;
                }
            } else {
                log.warn("활동 로그 기록 실패: userUuid가 없어 organizationId를 확인할 수 없습니다.");
                return;
            }
        }

        UserActivityLog activityLog = UserActivityLog.createLog(uuid, userUuid, groupId, effectiveOrganizationId,
                action, description,
                userAgent, createdBy, createdIp);
        userActivityLogRepository.save(activityLog);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserActivityLog> getUserActivities(String uuid) {
        return userActivityLogRepository.findByUserUuidOrderByCreatedAtDesc(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserActivityLog> getUserActivitiesByDateRange(String uuid, LocalDateTime startDate,
            LocalDateTime endDate) {
        return userActivityLogRepository.findByUserUuidAndCreatedAtBetweenOrderByCreatedAtDesc(uuid, startDate,
                endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public UserActivityLog getUserActivityLog(String uuid, String logId) {
        return userActivityLogRepository.findById(logId)
                .filter(log -> log.getUserUuid().equals(uuid))
                .orElseThrow(() -> new RuntimeException("Activity log not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserActivityLog> getUserActivityLogs(String uuid, LocalDateTime startDate, LocalDateTime endDate) {
        return userActivityLogRepository.findByUserUuidAndCreatedAtBetweenOrderByCreatedAtDesc(
                uuid, startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserActivityLogDto> getActivityLogs(Pageable pageable) {
        return userActivityLogRepository.findAll(pageable)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserActivityLogDto> getActivityLogsByUser(String uuid, Pageable pageable) {
        return userActivityLogRepository.findByUserUuid(uuid, pageable)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UserActivityLogDto getActivityLog(String logId) {
        return userActivityLogRepository.findById(logId)
                .map(this::convertToDto)
                .orElseThrow(() -> new RuntimeException("Activity log not found"));
    }

    @Override
    @Transactional
    public void deleteActivityLog(String logId) {
        userActivityLogRepository.deleteById(logId);
    }

    private UserActivityLogDto convertToDto(UserActivityLog log) {
        return UserActivityLogDto.builder()
                .uuid(log.getUuid())
                .activityType(log.getActivityType())
                .description(log.getDescription())
                .userAgent(log.getUserAgent())
                .createdBy(log.getCreatedBy())
                .createdIp(log.getCreatedIp())
                .createdAt(log.getCreatedAt())
                .updatedBy(log.getUpdatedBy())
                .updatedIp(log.getUpdatedIp())
                .updatedAt(log.getUpdatedAt())
                .build();
    }
}