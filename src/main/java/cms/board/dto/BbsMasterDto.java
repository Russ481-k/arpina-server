package cms.board.dto;

import cms.board.domain.BbsMasterDomain.BbsSkinType;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "게시판 마스터 정보")
public class BbsMasterDto {
    @Schema(description = "게시판 ID")
    private Long bbsId;

    @Schema(description = "연결된 메뉴 ID")
    private Long menuId;

    @Schema(description = "게시판 이름")
    @NotBlank(message = "게시판 이름은 필수 입력값입니다.")
    @Size(max = 100, message = "게시판 이름은 100자 이하여야 합니다.")
    private String bbsName;

    @Schema(description = "게시판 스킨 타입")
    @NotNull(message = "게시판 스킨 타입은 필수 입력값입니다.")
    private BbsSkinType skinType;

    @Schema(description = "읽기 권한")
    @NotBlank(message = "읽기 권한은 필수 입력값입니다.")
    private String readAuth;

    @Schema(description = "쓰기 권한")
    @NotBlank(message = "쓰기 권한은 필수 입력값입니다.")
    private String writeAuth;

    @Schema(description = "관리 권한")
    @NotBlank(message = "관리 권한은 필수 입력값입니다.")
    private String adminAuth;

    @Schema(description = "노출 여부", example = "Y", allowableValues = {"Y", "N"})
    private String displayYn;

    @Schema(description = "정렬 순서", example = "D", allowableValues = {"A", "D"})
    private String sortOrder;

    @Schema(description = "공지 기능", example = "N", allowableValues = {"Y", "N"})
    private String noticeYn;

    @Schema(description = "게시 기능", example = "N", allowableValues = {"Y", "N"})
    private String publishYn;

    @Schema(description = "첨부파일 기능", example = "N", allowableValues = {"Y", "N"})
    private String attachmentYn;

    @Schema(description = "첨부파일 제한 개수")
    private int attachmentLimit;

    @Schema(description = "첨부파일 제한 크기(MB)")
    private int attachmentSize;

    @Schema(description = "추가 스키마(JSON)")
    private JsonNode extraSchema;
} 