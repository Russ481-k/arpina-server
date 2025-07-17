package cms.content.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
public class ContentBlockCreateRequest {

    @NotEmpty
    private String type;

    private String content;

    private Long fileId;

    @NotNull
    private Integer sortOrder;
}