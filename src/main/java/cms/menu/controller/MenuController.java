package cms.menu.controller;

import cms.menu.dto.MenuDto;
import cms.menu.dto.MenuOrderDto;
import cms.menu.dto.PageDetailsDto;
import cms.menu.service.MenuService;
import cms.common.dto.ApiResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import cms.menu.domain.MenuType;

import java.util.List;

@RestController
@RequestMapping("/cms/menu")
@RequiredArgsConstructor
@Tag(name = "cms_01_Menu", description = "메뉴 관리 API")
public class MenuController {

    private final MenuService menuService;
    private static final Logger log = LoggerFactory.getLogger(MenuController.class);

    @Operation(summary = "메뉴 목록 조회", description = "모든 메뉴 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<List<MenuDto>>> getMenus() {
        List<MenuDto> menus = menuService.getMenus();
        return ResponseEntity.ok(ApiResponseSchema.success(menus, "메뉴 목록이 성공적으로 조회되었습니다."));
    }

    @Operation(summary = "메뉴 생성", description = "새로운 메뉴를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponseSchema<MenuDto>> createMenu(@RequestBody MenuDto menuDto) {
        MenuDto createdMenu = menuService.createMenu(menuDto);
        return ResponseEntity.ok(ApiResponseSchema.success(createdMenu, "메뉴가 성공적으로 생성되었습니다."));
    }

    @Operation(summary = "메뉴 수정", description = "기존 메뉴를 수정합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseSchema<MenuDto>> updateMenu(
            @Parameter(description = "메뉴 ID") @PathVariable Long id,
            @RequestBody MenuDto menuDto) {
        MenuDto updatedMenu = menuService.updateMenu(id, menuDto);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedMenu, "메뉴가 성공적으로 수정되었습니다."));
    }

    @Operation(summary = "메뉴 삭제", description = "기존 메뉴를 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseSchema<Void>> deleteMenu(
            @Parameter(description = "메뉴 ID") @PathVariable Long id) {
        menuService.deleteMenu(id);
        return ResponseEntity.ok(ApiResponseSchema.success("메뉴가 성공적으로 삭제되었습니다."));
    }

    @Operation(summary = "메뉴 조회", description = "특정 메뉴의 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseSchema<MenuDto>> getMenu(
            @Parameter(description = "메뉴 ID") @PathVariable Long id) {
        MenuDto menu = menuService.getMenu(id);
        return ResponseEntity.ok(ApiResponseSchema.success(menu, "메뉴 정보를 성공적으로 조회했습니다."));
    }

    @Operation(summary = "메뉴 트리 조회", description = "메뉴 트리 구조를 조회합니다.")
    @GetMapping("/tree")
    public ResponseEntity<ApiResponseSchema<List<MenuDto>>> getMenuTree() {
        List<MenuDto> menuTree = menuService.getMenuTree();
        return ResponseEntity.ok(ApiResponseSchema.success(menuTree, "메뉴 트리를 성공적으로 조회했습니다."));
    }

    @Operation(summary = "메뉴 활성화 상태 변경", description = "메뉴의 활성화 상태를 변경합니다.")
    @PutMapping("/{id}/active")
    public ResponseEntity<ApiResponseSchema<Void>> updateMenuActive(
            @Parameter(description = "메뉴 ID") @PathVariable Long id,
            @Parameter(description = "활성화 여부") @RequestParam boolean isActive) {
        menuService.updateMenuActive(id, isActive);
        return ResponseEntity.ok(ApiResponseSchema.success("메뉴 활성화 상태가 성공적으로 변경되었습니다."));
    }

    @Operation(summary = "메뉴 순서 변경", description = "메뉴의 순서를 변경합니다.")
    @PutMapping("/{id}/order")
    public ResponseEntity<ApiResponseSchema<Void>> updateMenuOrder(
            @Parameter(description = "메뉴 ID") @PathVariable Long id,
            @Parameter(description = "새로운 순서") @RequestParam int order) {
        menuService.updateMenuOrder(id, order);
        return ResponseEntity.ok(ApiResponseSchema.success("메뉴 순서가 성공적으로 변경되었습니다."));
    }

    @Operation(summary = "공개 메뉴 조회", description = "활성화된 메뉴만 조회합니다.")
    @GetMapping("/public")
    public ResponseEntity<ApiResponseSchema<List<MenuDto>>> getPublicMenus() {
        List<MenuDto> activeMenus = menuService.getActiveMenus();
        return ResponseEntity.ok(ApiResponseSchema.success(activeMenus, "활성화된 메뉴 목록이 성공적으로 조회되었습니다."));
    }

    @Operation(summary = "메뉴 순서 일괄 업데이트", description = "여러 메뉴의 순서를 한 번에 업데이트합니다.")
    @PutMapping("/order")
    public ResponseEntity<ApiResponseSchema<List<MenuDto>>> updateMenuOrders(
            @RequestBody List<MenuOrderDto> orders) {
        log.debug("Received menu order update request: {}", orders);
        try {
            List<MenuDto> updatedMenus = menuService.updateMenuOrders(orders);
            log.debug("Menu order update successful: {}", updatedMenus);
            return ResponseEntity.ok(ApiResponseSchema.success(updatedMenus, "메뉴 순서가 성공적으로 업데이트되었습니다."));
        } catch (IllegalArgumentException e) {
            log.error("Invalid menu order update request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponseSchema.error("잘못된 메뉴 순서 업데이트 요청입니다: " + e.getMessage(), "INVALID_REQUEST"));
        } catch (Exception e) {
            log.error("Error updating menu orders: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponseSchema.error("메뉴 순서 업데이트 중 오류가 발생했습니다.", "INTERNAL_SERVER_ERROR"));
        }
    }

    @Operation(summary = "타입별 메뉴 조회", description = "특정 타입의 활성화된 메뉴를 조회합니다.")
    @GetMapping("/type/{type}/active")
    public ResponseEntity<ApiResponseSchema<List<MenuDto>>> getActiveMenusByType(
            @Parameter(description = "메뉴 타입 (예: BOARD, CONTENT)") @PathVariable String type) {
        List<MenuDto> menus = menuService.getActiveMenusByType(type);
        return ResponseEntity.ok(ApiResponseSchema.success(menus, "타입별 메뉴 목록이 성공적으로 조회되었습니다."));
    }

    @Operation(summary = "타입별 메뉴 조회 (페이징)", description = "특정 타입의 메뉴를 페이징하여 조회합니다.")
    @GetMapping("/type/{type}")
    public ResponseEntity<Page<MenuDto>> getMenusByType(
            @Parameter(description = "메뉴 타입") @PathVariable MenuType type,
            Pageable pageable) {
        return ResponseEntity.ok(menuService.getMenusByType(type, pageable));
    }

    @Operation(summary = "공용 페이지 상세 정보 조회", description = "메뉴 ID를 기반으로 해당 공용 페이지에 표시될 상세 정보를 조회합니다.")
    @GetMapping("/public/{id}/page-details")
    public ResponseEntity<ApiResponseSchema<PageDetailsDto>> getPageDetails(
            @Parameter(description = "메뉴 ID") @PathVariable Long id) {
        PageDetailsDto pageDetails = menuService.getPageDetailsByMenuId(id);
        return ResponseEntity.ok(ApiResponseSchema.success(pageDetails, "페이지 상세 정보가 성공적으로 조회되었습니다."));
    }
} 