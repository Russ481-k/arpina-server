package cms.groupreservation.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class GroupReservationRequest {

    private String eventType;
    private String eventName;
    private String seatingArrangement;
    private Integer adultAttendees;
    private Integer childAttendees;
    private Boolean diningServiceUsage;
    private String otherRequests;

    @NotBlank(message = "단체명은 필수입니다.")
    private String customerGroupName;

    private String customerRegion;

    @NotBlank(message = "담당자 이름은 필수입니다.")
    private String contactPersonName;

    private String contactPersonDpt;

    @NotBlank(message = "담당자 휴대전화는 필수입니다.")
    @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "휴대전화 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    private String contactPersonPhone;

    @NotBlank(message = "담당자 연락처는 필수입니다.")
    private String contactPersonTel;

    @NotBlank(message = "담당자 이메일은 필수입니다.")
    @Email(message = "유효한 이메일 주소를 입력해주세요.")
    private String contactPersonEmail;

    @NotNull(message = "개인정보 수집 동의는 필수입니다.")
    private Boolean privacyAgreed;

    @NotNull(message = "마케팅 정보 수신 동의는 필수입니다.")
    private Boolean marketingAgreed;

    @Valid
    @NotNull(message = "회의실 예약 정보는 필수입니다.")
    @Size(min = 1, message = "최소 하나 이상의 회의실 예약 정보가 필요합니다.")
    private List<RoomReservationRequest> roomReservations;

    @Getter
    @Setter
    public static class RoomReservationRequest {
        @NotBlank(message = "회의실 분류는 필수입니다.")
        private String roomSizeDesc;

        @NotBlank(message = "회의실 이름은 필수입니다.")
        private String roomTypeDesc;

        @NotNull(message = "행사 시작일은 필수입니다.")
        private LocalDate startDate;

        @NotNull(message = "행사 종료일은 필수입니다.")
        private LocalDate endDate;

        @NotBlank(message = "사용 시간 설명은 필수입니다.")
        private String usageTimeDesc;
    }
}