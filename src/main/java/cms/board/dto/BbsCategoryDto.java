package cms.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Schema(description = "게시판 카테고리 정보")
public class BbsCategoryDto {
    @Schema(description = "카테고리 ID")
    private Long categoryId;

    @Schema(description = "카테고리 코드")
    private String code;

    @Schema(description = "카테고리 이름")
    private String name;
}