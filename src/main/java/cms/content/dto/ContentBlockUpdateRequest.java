package cms.content.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ContentBlockUpdateRequest {

    @NotEmpty
    private String type;

    private String content;

    private List<Long> fileIds;
}