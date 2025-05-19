package cms.mypage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TempPasswordRequest {
    @Schema(description = "사용자 ID (username)", example = "testuser", required = true)
    private String userId;
} 