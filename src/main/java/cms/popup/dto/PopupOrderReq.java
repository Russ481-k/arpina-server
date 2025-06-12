package cms.popup.dto;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class PopupOrderReq {

    @NotEmpty
    private List<Long> orderedIds;

}