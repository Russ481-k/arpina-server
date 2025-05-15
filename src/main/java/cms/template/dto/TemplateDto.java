package cms.template.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import cms.template.domain.TemplateType;
import lombok.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "템플릿 정보")
public class TemplateDto {
    @Schema(description = "템플릿 ID")
    private Long id;

    @Schema(description = "템플릿 이름")
    @NotBlank(message = "템플릿명은 필수 입력값입니다.")
    @Size(max = 100, message = "템플릿명은 100자 이내여야 합니다.")
    private String templateName;

    @Schema(description = "템플릿 설명")
    @Size(max = 500, message = "템플릿 설명은 500자 이내여야 합니다.")
    private String description;

    @Schema(description = "템플릿 타입")
    private TemplateType type;

    @Schema(description = "게시 여부")
    private boolean published;

    @Schema(description = "버전 번호")
    private int versionNo;

    @Schema(description = "템플릿 행 목록")
    private List<TemplateRowDto> rows;
} 