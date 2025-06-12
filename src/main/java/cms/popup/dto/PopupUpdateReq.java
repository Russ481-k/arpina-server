package cms.popup.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PopupUpdateReq {

    private String title;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Boolean isVisible;

    private Integer displayOrder;

}