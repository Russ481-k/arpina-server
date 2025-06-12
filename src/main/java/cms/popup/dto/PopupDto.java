package cms.popup.dto;

import cms.popup.domain.Popup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopupDto {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean isVisible;
    private Integer displayOrder;
    private String createdBy;
    private String createdIp;
    private LocalDateTime createdAt;
    private String updatedBy;
    private String updatedIp;
    private LocalDateTime updatedAt;

    public static PopupDto from(Popup entity) {
        return PopupDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .isVisible(entity.isVisible())
                .displayOrder(entity.getDisplayOrder())
                .createdBy(entity.getCreatedBy())
                .createdIp(entity.getCreatedIp())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedIp(entity.getUpdatedIp())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}