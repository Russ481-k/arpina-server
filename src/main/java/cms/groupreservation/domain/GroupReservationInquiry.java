package cms.groupreservation.domain;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "group_reservation_inquiries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupReservationInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(length = 50)
    private String eventType;

    @Column(length = 255)
    private String eventName;

    @Column(length = 20)
    private String seatingArrangement;

    private Integer adultAttendees;

    private Integer childAttendees;

    private Boolean diningServiceUsage;

    @Lob
    private String otherRequests;

    @Column(length = 100, nullable = false)
    private String customerGroupName;

    @Column(length = 50)
    private String customerRegion;

    @Column(length = 50, nullable = false)
    private String contactPersonName;

    @Column(length = 100)
    private String contactPersonDpt;

    @Column(length = 20, nullable = false)
    private String contactPersonPhone;

    @Column(length = 20, nullable = false)
    private String contactPersonTel;

    @Column(length = 100, nullable = false)
    private String contactPersonEmail;

    @Column(nullable = false)
    private Boolean privacyAgreed;

    @Column(nullable = false)
    private Boolean marketingAgreed;

    @Lob
    private String adminMemo;

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

    @Builder.Default
    @OneToMany(mappedBy = "inquiry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InquiryRoomReservation> roomReservations = new ArrayList<>();

    public void addRoomReservation(InquiryRoomReservation roomReservation) {
        roomReservations.add(roomReservation);
        roomReservation.setInquiry(this);
    }
}