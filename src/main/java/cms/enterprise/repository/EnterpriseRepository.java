package cms.enterprise.repository;

import cms.enterprise.domain.EnterpriseDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EnterpriseRepository extends JpaRepository<EnterpriseDomain, Long> {

    // 연도별 필터링, 이름 검색, 페이지네이션 및 정렬을 포함하는 메소드 예시
    // QueryDSL을 사용하지 않는 경우, JPQL 또는 네이티브 쿼리로 복잡한 검색 구현 필요
    // 여기서는 간단한 연도 필터링과 이름 검색(like) 예시를 JPQL로 작성

    /**
     * 연도(선택), 기업명(선택, 부분 일치)으로 기업 목록을 조회하고 페이지네이션 및 정렬을 적용합니다.
     *
     * @param year 검색할 연도 (null이면 모든 연도)
     * @param name 검색할 기업명 (null이면 모든 기업명, 부분 일치)
     * @param pageable 페이지네이션 및 정렬 정보
     * @return 페이징 처리된 기업 목록
     */
    @Query("SELECT e FROM EnterpriseDomain e WHERE " +
           "(:year IS NULL OR e.year = :year) AND " +
           "(:name IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:representative IS NULL OR LOWER(e.representative) LIKE LOWER(CONCAT('%', :representative, '%'))) AND " +
           "(:businessType IS NULL OR LOWER(e.businessType) LIKE LOWER(CONCAT('%', :businessType, '%')))")
    Page<EnterpriseDomain> findByYearAndNameContainingFilters(
            @Param("year") Integer year,
            @Param("name") String name,
            @Param("representative") String representative, // 추가 검색 필드
            @Param("businessType") String businessType,   // 추가 검색 필드
            Pageable pageable);

    // ID로 조회 (JpaRepository 기본 제공 findById 사용)
    // 이름으로 중복 체크 (필요시)
    Optional<EnterpriseDomain> findByNameAndYear(String name, Integer year);
} 