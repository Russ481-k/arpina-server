package cms.popup.repository;

import cms.popup.domain.Popup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    @Query("SELECT p FROM Popup p WHERE p.isVisible = true AND :now BETWEEN p.startDate AND p.endDate ORDER BY p.displayOrder ASC, p.createdAt DESC")
    List<Popup> findActivePopups(@Param("now") LocalDateTime now);

    @Query("SELECT p FROM Popup p ORDER BY p.displayOrder ASC, p.createdAt DESC")
    List<Popup> findAllByOrderByDisplayOrderAsc();

    Optional<Popup> findById(Long id);
}