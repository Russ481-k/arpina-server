package cms.popup.dto;

import cms.popup.domain.Popup;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PopupRes {

    private Long id;
    private String title;
    private String content;
    private Integer displayOrder;

    public static PopupRes from(Popup entity) {
        return PopupRes.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .displayOrder(entity.getDisplayOrder())
                .build();
    }
}