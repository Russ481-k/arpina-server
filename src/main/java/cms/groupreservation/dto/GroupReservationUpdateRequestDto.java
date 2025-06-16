package cms.groupreservation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupReservationUpdateRequestDto {
    private String status;
    private String memo;
}