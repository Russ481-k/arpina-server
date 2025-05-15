package feature.repository;

import feature.domain.Feature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, Long> {
    @Query("SELECT f FROM Feature f WHERE " +
           "f.name LIKE %:keyword% OR " +
           "f.description LIKE %:keyword%")
    Page<Feature> searchFeatures(@Param("keyword") String keyword, Pageable pageable);

    Page<Feature> findByType(String type, Pageable pageable);

    Page<Feature> findByIsActive(boolean isActive, Pageable pageable);
} 