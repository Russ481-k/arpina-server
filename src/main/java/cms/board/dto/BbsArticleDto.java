package cms.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import cms.file.dto.AttachmentInfoDto;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "게시글 정보")
public class BbsArticleDto {
    @Schema(description = "게시글 ID")
    private Long nttId;

    @Schema(description = "게시판 ID")
    @NotNull(message = "게시판 ID는 필수 입력값입니다.")
    private Long bbsId;

    @Schema(description = "부모 게시글 ID")
    private Long parentNttId;

    @Schema(description = "답변 깊이")
    private Integer threadDepth;

    @Schema(description = "작성자")
    @NotBlank(message = "작성자는 필수 입력값입니다.")
    @Size(max = 50, message = "작성자는 50자 이하여야 합니다.")
    private String writer;

    @Schema(description = "제목")
    @NotBlank(message = "제목은 필수 입력값입니다.")
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    private String title;

    @Schema(description = "내용")
    @NotBlank(message = "내용은 필수 입력값입니다.")
    private String content;

    @Schema(description = "공지 상태", example = "N", allowableValues = {"N", "Y", "P"})
    private String noticeState;

    @Schema(description = "공지 시작일")
    private LocalDateTime noticeStartDt;

    @Schema(description = "공지 종료일")
    private LocalDateTime noticeEndDt;

    @Schema(description = "게시 상태", example = "Y", allowableValues = {"N", "Y", "P"})
    private String publishState;

    @Schema(description = "게시 시작일")
    private LocalDateTime publishStartDt;

    @Schema(description = "게시 종료일")
    private LocalDateTime publishEndDt;

    @Schema(description = "외부 링크")
    @Size(max = 255, message = "외부 링크는 255자 이하여야 합니다.")
    private String externalLink;

    @Schema(description = "조회수")
    private Integer hits;

    @Schema(description = "내용 중 이미지 포함 여부")
    private boolean hasImageInContent;

    @Schema(description = "첨부파일 존재 여부")
    private boolean hasAttachment;

    @Schema(description = "생성 일시")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시")
    private LocalDateTime updatedAt;

    @Schema(description = "첨부파일 정보 목록")
    private List<AttachmentInfoDto> attachments;

    @Schema(description = "게시판 스킨 타입")
    private String skinType;

    @Schema(description = "메뉴 ID")
    @NotNull(message = "메뉴 ID는 필수 입력값입니다.")
    private Long menuId;

    @Schema(description = "순번")
    private Integer no;
} 