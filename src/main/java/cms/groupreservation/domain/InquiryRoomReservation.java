package cms.groupreservation.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inquiry_room_reservations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryRoomReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_id", nullable = false)
    private GroupReservationInquiry inquiry;

    @Column(length = 50)
    private String roomSizeDesc;

    @Column(length = 50)
    private String roomTypeDesc;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(length = 100)
    private String usageTimeDesc;

    @Column(length = 50)
    private String createdBy;

    @Column(length = 45)
    private String createdIp;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdDate;

    @Column(length = 50)
    private String updatedBy;

    @Column(length = 45)
    private String updatedIp;

    @UpdateTimestamp
    private LocalDateTime updatedDate;
}