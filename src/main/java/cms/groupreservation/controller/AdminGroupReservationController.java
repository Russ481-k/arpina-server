package cms.groupreservation.controller;

import cms.common.dto.ApiResponseSchema;
import cms.groupreservation.dto.GroupReservationInquiryDto;
import cms.groupreservation.dto.GroupReservationUpdateRequestDto;
import cms.groupreservation.service.GroupReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cms/group-reservations")
@RequiredArgsConstructor
public class AdminGroupReservationController {

    private final GroupReservationService groupReservationService;

    @GetMapping
    public ResponseEntity<ApiResponseSchema<Page<GroupReservationInquiryDto>>> getInquiries(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @PageableDefault(sort = "createdDate", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<GroupReservationInquiryDto> inquiries = groupReservationService.getInquiries(pageable, type,
                search, status, eventType);
        return ResponseEntity.ok(ApiResponseSchema.success(inquiries));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseSchema<GroupReservationInquiryDto>> getInquiry(@PathVariable Long id) {
        GroupReservationInquiryDto inquiry = groupReservationService.getInquiry(id);
        return ResponseEntity.ok(ApiResponseSchema.success(inquiry));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponseSchema<GroupReservationInquiryDto>> updateInquiry(
            @PathVariable Long id,
            @RequestBody GroupReservationUpdateRequestDto requestDto) {
        GroupReservationInquiryDto updatedInquiry = groupReservationService.updateInquiry(id, requestDto);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedInquiry));
    }
}