package cms.template.service.dto;

import java.time.LocalDateTime;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class TemplateDto {
    private Long id;
    
    @NotBlank(message = "템플릿명은 필수 입력값입니다.")
    @Size(max = 100, message = "템플릿명은 100자 이내여야 합니다.")
    private String templateName;
    
    @Size(max = 500, message = "템플릿 설명은 500자 이내여야 합니다.")
    private String description;
    
    private String templateType;  // LAYOUT, COMPONENT, PAGE 등
    
    private boolean isDefault;    // 기본 템플릿 여부
    
    // 메타 정보
    private String createdBy;
    private String updatedBy;

    private String name;
    private String content;
    private String type;

    private boolean published;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void setName(String name) {
        this.name = name;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setType(String type) {
        this.type = type;
    }
} 