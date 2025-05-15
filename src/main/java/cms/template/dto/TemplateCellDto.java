package cms.template.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "템플릿 셀 정보")
public class TemplateCellDto {
    @Schema(description = "셀 ID")
    private Long cellId;

    @Schema(description = "셀 순서")
    private int cellOrder;

    @Schema(description = "셀 너비(%)")
    private int widthPercent;

    @Schema(description = "셀 내용")
    private String content;

    @Schema(description = "셀 스타일")
    private String style;

    @Schema(description = "셀 순서")
    private int ordinal;

    @Schema(description = "셀 스팬 정보")
    private Map<String, Integer> span;

    @Schema(description = "위젯 ID")
    private Long widgetId;
} 