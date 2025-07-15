package cms.board.dto;

import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BbsCommentRequest {
    @NotBlank(message = "댓글 내용은 필수입니다.")
    private String content;
    private String displayWriter;
}