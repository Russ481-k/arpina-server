package cms.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Schema(description = "게시글 첨부파일 요약 정보 DTO")
public class AttachmentInfoDto {

    @Schema(description = "파일 ID")
    private Long fileId;

    @Schema(description = "원본 파일명")
    private String originName;

    @Schema(description = "파일 크기 (bytes)")
    private Long size;

    @Schema(description = "MIME 타입")
    private String mimeType;
    
    @Schema(description = "파일 확장자")
    private String ext;

    @Schema(description = "파일 다운로드 URL")
    private String downloadUrl;
} 