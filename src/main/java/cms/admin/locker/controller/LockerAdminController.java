package cms.admin.locker.controller;

import cms.admin.locker.dto.LockerInventoryDto;
import cms.admin.locker.dto.LockerInventoryUpdateRequestDto;
import cms.admin.locker.service.LockerAdminService;
import cms.common.dto.ApiResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "CMS - Locker Inventory Management", description = "사물함 재고 관리 API (관리자용)")
@RestController
@RequestMapping("/cms/lockers/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class LockerAdminController {

    private final LockerAdminService lockerAdminService;

    @Operation(summary = "모든 성별 라커 재고 현황 조회", description = "MALE, FEMALE 각 성별의 라커 총량, 사용량, 가용량을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<List<LockerInventoryDto>>> getAllLockerInventories() {
        List<LockerInventoryDto> inventories = lockerAdminService.getAllLockerInventories();
        return ResponseEntity.ok(ApiResponseSchema.success(inventories, "전체 라커 재고 조회 성공"));
    }

    @Operation(summary = "특정 성별 라커 재고 현황 조회", description = "특정 성별(MALE 또는 FEMALE)의 라커 재고 정보를 조회합니다.")
    @GetMapping("/{gender}")
    public ResponseEntity<ApiResponseSchema<LockerInventoryDto>> getLockerInventoryByGender(
            @Parameter(description = "조회할 성별 (MALE 또는 FEMALE)", example = "MALE") @PathVariable String gender) {
        LockerInventoryDto inventoryDto = lockerAdminService.getLockerInventoryByGender(gender);
        return ResponseEntity.ok(ApiResponseSchema.success(inventoryDto, "특정 성별 라커 재고 조회 성공"));
    }

    @Operation(summary = "특정 성별 라커 총 수량 수정", description = "특정 성별(MALE 또는 FEMALE)의 전체 라커 수량을 수정합니다. 사용 중인 수량보다 적게 설정할 수 없습니다.")
    @PutMapping("/{gender}")
    public ResponseEntity<ApiResponseSchema<LockerInventoryDto>> updateTotalLockerQuantity(
            @Parameter(description = "수정할 성별 (MALE 또는 FEMALE)", example = "MALE") @PathVariable String gender,
            @Valid @RequestBody LockerInventoryUpdateRequestDto updateRequestDto) {
        LockerInventoryDto updatedInventory = lockerAdminService.updateTotalLockerQuantity(gender, updateRequestDto);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedInventory, "라커 총 수량 수정 성공"));
    }
} 