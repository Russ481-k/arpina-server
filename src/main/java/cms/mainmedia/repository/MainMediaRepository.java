package cms.mainmedia.repository;

import cms.mainmedia.domain.MainMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MainMediaRepository extends JpaRepository<MainMedia, Long> {
    List<MainMedia> findAllByOrderByDisplayOrderAsc();
}