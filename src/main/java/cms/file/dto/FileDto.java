package cms.file.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FileDto {
    private Long fileId;
    private String menu;
    private Long menuId;
    private String originName;
    private String savedName;
    private String mimeType;
    private Long size;
    private String ext;
    private Integer version;
    private String publicYn;
    private Integer fileOrder;
    private String createdBy;
    private String createdIp;
    private LocalDateTime createdDate;
    private String updatedBy;
    private String updatedIp;
    private LocalDateTime updatedDate;
} 