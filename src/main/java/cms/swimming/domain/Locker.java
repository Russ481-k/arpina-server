package cms.swimming.domain;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "locker")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Locker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "locker_id")
    private Long lockerId;

    @Column(name = "locker_number", nullable = false)
    private String lockerNumber;

    @Column(name = "zone", nullable = false)
    private String zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private LockerGender gender;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    public enum LockerGender {
        M, F
    }

    // 라커 활성화 상태 변경
    public void toggleActive(Boolean isActive) {
        this.isActive = isActive;
    }

    // 라커 정보 업데이트
    public void updateDetails(String lockerNumber, String zone, LockerGender gender, Boolean isActive) {
        this.lockerNumber = lockerNumber;
        this.zone = zone;
        this.gender = gender;
        this.isActive = isActive;
    }
} 