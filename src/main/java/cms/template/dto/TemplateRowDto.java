package cms.template.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "템플릿 행 정보")
public class TemplateRowDto {
    @Schema(description = "행 순서")
    private Integer ordinal;

    @Schema(description = "행 높이(픽셀)")
    private int heightPx;

    @Schema(description = "배경색")
    private String bgColor;

    @Schema(description = "셀 목록")
    private List<TemplateCellDto> cells;
} 