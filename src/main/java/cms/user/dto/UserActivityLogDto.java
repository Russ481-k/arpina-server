package cms.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLogDto {
    private String uuid;
    private String activityType;
    private String description;
    private String userAgent;
    private String createdBy;
    private String createdIp;
    private LocalDateTime createdAt;
    private String updatedBy;
    private String updatedIp;
    private LocalDateTime updatedAt;
} 