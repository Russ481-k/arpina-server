package cms.popup.dto;

import cms.popup.domain.Popup;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminPopupRes {

    private Long id;
    private String title;
    private boolean isVisible;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminPopupRes from(Popup entity) {
        return AdminPopupRes.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .isVisible(entity.isVisible())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .displayOrder(entity.getDisplayOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}