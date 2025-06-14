package cms.groupreservation.repository;

import cms.groupreservation.domain.InquiryRoomReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRoomReservationRepository extends JpaRepository<InquiryRoomReservation, Long> {
}