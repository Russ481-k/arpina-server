package cms.content.dto;

import cms.content.domain.ContentStatus;
import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Getter
@Setter
public class ContentDto {
    private Long id;
    
    @NotBlank(message = "제목은 필수 입력값입니다.")
    @Size(max = 200, message = "제목은 200자 이내여야 합니다.")
    private String title;
    
    @NotBlank(message = "내용은 필수 입력값입니다.")
    private String content;
    
    @Size(max = 100, message = "작성자는 100자 이내여야 합니다.")
    private String author;
    
    private String contentType;  // ARTICLE, NOTICE, FAQ 등
    
    private boolean isPublished;
    
    private int viewCount;
    
    private LocalDateTime publishedAt;
    
    private LocalDateTime expiredAt;
    
    // 메타 정보
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 버전 정보
    private Long versionId;
    private String versionComment;
    
    // 첨부파일 정보
    private String attachedFiles;
    
    // 다국어 지원
    private String languageCode;  // ko, en, ja 등
    
    private String type;

    private String description;

    private ContentStatus status;

    public void setType(String type) {
        this.type = type;
    }
} 