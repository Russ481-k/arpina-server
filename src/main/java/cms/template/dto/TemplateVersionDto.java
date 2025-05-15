package cms.template.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateVersionDto {
    private Long id;
    private String version;
    private String layout;
    private String comment;
    private LocalDateTime createdAt;
} 