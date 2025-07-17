package cms.groupreservation.controller;

import cms.common.dto.ApiResponseSchema;
import cms.groupreservation.dto.GroupReservationRequest;
import cms.groupreservation.service.GroupReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/group-reservations")
@RequiredArgsConstructor
@Tag(name = "public_group_reservation", description = "공개 단체 예약 문의 API")
public class PublicGroupReservationController {

    private final GroupReservationService groupReservationService;

    @PostMapping
    @Operation(summary = "단체 예약 문의 생성", description = "사용자가 단체 예약을 문의합니다.")
    public ApiResponseSchema<Long> createInquiry(@Valid @RequestBody GroupReservationRequest request) {
        return ApiResponseSchema.success(groupReservationService.createInquiry(request), "문의가 성공적으로 등록되었습니다.");
    }
}