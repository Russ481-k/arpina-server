package cms.groupreservation.dto;

import cms.groupreservation.domain.GroupReservationInquiry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupReservationInquiryDto {
    private Long id;
    private String status;
    private String eventType;
    private String eventName;
    private String seatingArrangement;
    private Integer adultAttendees;
    private Integer childAttendees;
    private Boolean diningServiceUsage;
    private String otherRequests;
    private String customerGroupName;
    private String customerRegion;
    private String contactPersonName;
    private String contactPersonDpt;
    private String contactPersonPhone;
    private String contactPersonTel;
    private String contactPersonEmail;
    private Boolean privacyAgreed;
    private Boolean marketingAgreed;
    private String adminMemo;
    private String createdBy;
    private String createdIp;
    private LocalDateTime createdDate;
    private String updatedBy;
    private String updatedIp;
    private LocalDateTime updatedDate;
    private List<InquiryRoomReservationDto> roomReservations;

    public GroupReservationInquiryDto(GroupReservationInquiry entity) {
        this.id = entity.getId();
        this.status = entity.getStatus();
        this.eventType = entity.getEventType();
        this.eventName = entity.getEventName();
        this.seatingArrangement = entity.getSeatingArrangement();
        this.adultAttendees = entity.getAdultAttendees();
        this.childAttendees = entity.getChildAttendees();
        this.diningServiceUsage = entity.getDiningServiceUsage();
        this.otherRequests = entity.getOtherRequests();
        this.customerGroupName = entity.getCustomerGroupName();
        this.customerRegion = entity.getCustomerRegion();
        this.contactPersonName = entity.getContactPersonName();
        this.contactPersonDpt = entity.getContactPersonDpt();
        this.contactPersonPhone = entity.getContactPersonPhone();
        this.contactPersonTel = entity.getContactPersonTel();
        this.contactPersonEmail = entity.getContactPersonEmail();
        this.privacyAgreed = entity.getPrivacyAgreed();
        this.marketingAgreed = entity.getMarketingAgreed();
        this.adminMemo = entity.getAdminMemo();
        this.createdBy = entity.getCreatedBy();
        this.createdIp = entity.getCreatedIp();
        this.createdDate = entity.getCreatedDate();
        this.updatedBy = entity.getUpdatedBy();
        this.updatedIp = entity.getUpdatedIp();
        this.updatedDate = entity.getUpdatedDate();
        if (entity.getRoomReservations() != null) {
            this.roomReservations = entity.getRoomReservations().stream()
                    .map(InquiryRoomReservationDto::new)
                    .collect(Collectors.toList());
        }
    }
}