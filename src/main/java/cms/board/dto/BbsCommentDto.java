package cms.board.dto;

import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
public class BbsCommentDto {
    private Long commentId;
    private Long nttId;
    private String content;
    private String writer;
    private String displayWriter;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}