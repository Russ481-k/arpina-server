package cms.locker.controller;

import cms.locker.dto.LockerAvailabilityDto;
import cms.locker.service.LockerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Pattern;

@Tag(name = "Locker API", description = "사물함 관련 API")
@RestController
@RequestMapping("/lockers")
@RequiredArgsConstructor
public class LockerController {

    private final LockerService lockerService;

    @Operation(summary = "성별 사물함 잔여 현황 조회", description = "회원이 자신의 성별을 API 파라미터로 전달하여 해당 성별의 글로벌 잔여 사물함 수를 조회합니다.")
    @GetMapping("/availability/status")
    public ResponseEntity<LockerAvailabilityDto> getLockerAvailabilityStatus(
            @Parameter(description = "조회할 성별 (MALE 또는 FEMALE)", required = true, example = "MALE")
            @RequestParam @Pattern(regexp = "^(MALE|FEMALE)$", message = "성별은 MALE 또는 FEMALE 값이어야 합니다.") String gender) {
        LockerAvailabilityDto availabilityDto = lockerService.getLockerAvailabilityByGender(gender);
        return ResponseEntity.ok(availabilityDto);
    }
} 