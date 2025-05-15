package cms.notice.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NoticeDto {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime expiredAt;
    private boolean published;
    private int versionNo;
    private String createdBy;
    private LocalDateTime createdAt;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedAt;
} 