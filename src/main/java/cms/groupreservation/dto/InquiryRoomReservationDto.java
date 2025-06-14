package cms.groupreservation.dto;

import cms.groupreservation.domain.InquiryRoomReservation;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryRoomReservationDto {
    private Long id;
    private String roomSizeDesc;
    private String roomTypeDesc;
    private LocalDate startDate;
    private LocalDate endDate;
    private String usageTimeDesc;
    private String createdBy;
    private String createdIp;
    private LocalDateTime createdDate;
    private String updatedBy;
    private String updatedIp;
    private LocalDateTime updatedDate;

    public InquiryRoomReservationDto(InquiryRoomReservation entity) {
        this.id = entity.getId();
        this.roomSizeDesc = entity.getRoomSizeDesc();
        this.roomTypeDesc = entity.getRoomTypeDesc();
        this.startDate = entity.getStartDate();
        this.endDate = entity.getEndDate();
        this.usageTimeDesc = entity.getUsageTimeDesc();
        this.createdBy = entity.getCreatedBy();
        this.createdIp = entity.getCreatedIp();
        this.createdDate = entity.getCreatedDate();
        this.updatedBy = entity.getUpdatedBy();
        this.updatedIp = entity.getUpdatedIp();
        this.updatedDate = entity.getUpdatedDate();
    }
}