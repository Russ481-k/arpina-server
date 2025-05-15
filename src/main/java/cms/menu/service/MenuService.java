package cms.menu.service;

import cms.menu.domain.MenuType;
import cms.menu.dto.MenuDto;
import cms.menu.dto.MenuOrderDto;
import cms.menu.dto.PageDetailsDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MenuService {
    
    /**
     * 메뉴를 등록한다.
     * @param menuDto 메뉴 정보
     * @return 등록된 메뉴 ID
     */
    MenuDto createMenu(MenuDto menuDto);
    
    /**
     * 메뉴를 수정한다.
     * @param id 메뉴 ID
     * @param menuDto 메뉴 정보
     * @return 수정된 메뉴 정보
     */
    MenuDto updateMenu(Long id, MenuDto menuDto);
    
    /**
     * 메뉴를 삭제한다.
     * @param id 메뉴 ID
     */
    void deleteMenu(Long id);
    
    /**
     * 메뉴를 조회한다.
     * @param id 메뉴 ID
     * @return 메뉴 정보
     */
    MenuDto getMenu(Long id);
    
    /**
     * 메뉴 트리를 조회한다.
     * @return 메뉴 트리 목록
     */
    List<MenuDto> getMenuTree();
    
    /**
     * 메뉴의 순서를 변경한다.
     * @param menuId 메뉴 ID
     * @param newOrder 새로운 순서
     */
    void updateMenuOrder(Long menuId, int newOrder);
    
    /**
     * 메뉴들의 순서를 일괄 업데이트한다.
     * @param orders 업데이트할 메뉴 순서 목록
     * @return 업데이트된 메뉴 목록
     */
    List<MenuDto> updateMenuOrders(List<MenuOrderDto> orders);
    
    /**
     * 메뉴의 활성화 상태를 변경한다.
     * @param menuId 메뉴 ID
     * @param visible 활성화 여부
     */
    void updateMenuActive(Long menuId, boolean visible);
    
    /**
     * 활성화된 메뉴를 조회한다.
     * @return 활성화된 메뉴 목록
     */
    List<MenuDto> getActiveMenus();

    /**
     * 전체 메뉴를 조회한다.
     * @return 전체 메뉴 목록
     */
    List<MenuDto> getMenus();

    /**
     * 특정 타입의 활성화된 메뉴 목록을 조회합니다.
     * @param type 메뉴 타입 (예: "BOARD")
     * @return 메뉴 DTO 목록
     */
    List<MenuDto> getActiveMenusByType(String type);

    Page<MenuDto> getMenusByType(MenuType type, Pageable pageable);

    /**
     * 메뉴 ID를 기반으로 페이지 상세 정보를 조회한다.
     * @param menuId 메뉴 ID
     * @return 페이지 상세 정보 DTO
     */
    PageDetailsDto getPageDetailsByMenuId(Long menuId);
} 