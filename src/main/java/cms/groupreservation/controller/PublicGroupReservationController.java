package cms.groupreservation.controller;

import cms.common.dto.ApiResponseSchema;
import cms.groupreservation.dto.GroupReservationRequest;
import cms.groupreservation.service.GroupReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/group-reservations")
@RequiredArgsConstructor
public class PublicGroupReservationController {

    private final GroupReservationService groupReservationService;

    @PostMapping
    public ResponseEntity<ApiResponseSchema<Long>> createInquiry(
            @Valid @RequestBody GroupReservationRequest request,
            HttpServletRequest servletRequest) {
        Long inquiryId = groupReservationService.createInquiry(request, servletRequest);
        return new ResponseEntity<>(ApiResponseSchema.success(inquiryId), HttpStatus.CREATED);
    }
}