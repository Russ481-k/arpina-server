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

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserActivityLogServiceImpl implements UserActivityLogService {

    private final UserActivityLogRepository userActivityLogRepository;
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(UserActivityLogServiceImpl.class);

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(String uuid, String userUuid, String groupId, String organizationId, String action, String description,
                          String userAgent, String createdBy, String createdIp) {
        if (!userRepository.existsById(userUuid)) {
            log.warn("User not found with UUID: {}", userUuid);
            return;
        }

        UserActivityLog log = UserActivityLog.createLog(uuid, userUuid, groupId, organizationId, action, description,
                userAgent, createdBy, createdIp);
        userActivityLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserActivityLog> getUserActivities(String uuid) {
        return userActivityLogRepository.findByUserUuidOrderByCreatedAtDesc(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserActivityLog> getUserActivitiesByDateRange(String uuid, LocalDateTime startDate, LocalDateTime endDate) {
        return userActivityLogRepository.findByUserUuidAndCreatedAtBetweenOrderByCreatedAtDesc(uuid, startDate, endDate);
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