package cms.swimming.dto;

import cms.swimming.domain.Locker;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LockerDto {
    private Long lockerId;
    private String lockerNumber;
    private String zone;
    private String gender; // M, F
    private Boolean isActive;

    // 도메인 객체로 변환하는 메소드
    public Locker toEntity() {
        return Locker.builder()
                .lockerNumber(lockerNumber)
                .zone(zone)
                .gender(Locker.LockerGender.valueOf(gender))
                .isActive(isActive)
                .build();
    }

    // 도메인 객체에서 DTO로 변환하는 메소드
    public static LockerDto fromEntity(Locker locker) {
        return LockerDto.builder()
                .lockerId(locker.getLockerId())
                .lockerNumber(locker.getLockerNumber())
                .zone(locker.getZone())
                .gender(locker.getGender().name())
                .isActive(locker.getIsActive())
                .build();
    }
} 