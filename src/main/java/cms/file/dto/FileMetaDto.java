package cms.file.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetaDto {
    private Long fileId;
    private String module;
    private Long moduleId;
    private String originName;
    private String savedName;
    private String mimeType;
    private String ext;
    private Long size;
    private Integer version;
    private Boolean publicYn;
    private String thumbPath;
    private Integer fileOrder;
    private String createdBy;
    private String createdIp;
    private LocalDateTime createdAt;
    private String updatedBy;
    private String updatedIp;
    private LocalDateTime updatedAt;
} 