package cms.mainmedia.dto;

import cms.mainmedia.domain.MediaType;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class MainMediaRequestDto {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    private String description;

    @NotNull(message = "미디어 유형은 필수입니다.")
    private MediaType mediaType;

    @NotNull(message = "노출 순서는 필수입니다.")
    private Integer displayOrder;

    @NotNull(message = "파일 ID는 필수입니다.")
    private Long fileId;
}