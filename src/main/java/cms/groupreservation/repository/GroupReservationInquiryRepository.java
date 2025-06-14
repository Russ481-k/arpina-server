package cms.groupreservation.repository;

import cms.groupreservation.domain.GroupReservationInquiry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface GroupReservationInquiryRepository
        extends JpaRepository<GroupReservationInquiry, Long>, JpaSpecificationExecutor<GroupReservationInquiry> {

    @Override
    @EntityGraph(attributePaths = { "roomReservations" })
    Optional<GroupReservationInquiry> findById(Long id);
}