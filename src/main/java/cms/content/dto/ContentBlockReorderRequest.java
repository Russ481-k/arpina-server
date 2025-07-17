package cms.content.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ContentBlockReorderRequest {

    @NotNull
    private List<ReorderItem> reorderItems;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReorderItem {
        @NotNull
        private Long id;
        @NotNull
        private Integer sortOrder;
    }
}