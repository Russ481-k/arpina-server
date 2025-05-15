package cms.menu.repository;

import cms.menu.domain.Menu;
import cms.menu.domain.MenuType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findByVisibleTrue();

    /**
     * 특정 타입의 활성화된 메뉴를 조회합니다.
     * @param type 메뉴 타입
     * @return 메뉴 목록
     */
    List<Menu> findByTypeAndVisibleTrue(String type);

    Page<Menu> findByType(MenuType type, Pageable pageable);

    // Find the first menu linked to a specific target type (enum) and ID
    Optional<Menu> findFirstByTypeAndTargetId(MenuType type, Long targetId);
} 