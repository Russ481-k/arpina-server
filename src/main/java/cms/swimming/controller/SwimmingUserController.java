package cms.swimming.controller;

import cms.common.dto.ApiResponseSchema;
import cms.swimming.dto.CancelRequestDto;
import cms.swimming.dto.EnrollRequestDto;
import cms.swimming.dto.EnrollResponseDto;
import cms.swimming.dto.LessonDto;
import cms.swimming.dto.LockerDto;
import cms.enroll.service.EnrollmentService;
import cms.swimming.service.LessonService;
import cms.swimming.service.LockerService;
import cms.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/swimming")
@RequiredArgsConstructor
@Tag(name = "swimming_user", description = "수영장 사용자 API")
@Validated
public class SwimmingUserController {

    private final LessonService lessonService;
    private final LockerService lockerService;
    private final EnrollmentService enrollmentService;

    // 1. 수업 조회 API
    @Operation(summary = "열린 수업 목록 조회", description = "신청 가능한 수업 목록을 페이징하여 제공합니다.")
    @GetMapping("/lessons")
    public ResponseEntity<ApiResponseSchema<Page<LessonDto>>> getOpenLessons(
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<LessonDto> lessons = lessonService.getLessonsByStatus("OPEN", pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(lessons, "수업 목록 조회 성공"));
    }

    @Operation(summary = "특정 수업 상세 조회", description = "특정 수업의 상세 정보를 조회합니다.")
    @GetMapping("/lessons/{lessonId}")
    public ResponseEntity<ApiResponseSchema<LessonDto>> getLessonDetail(
            @Parameter(description = "조회할 수업 ID") @PathVariable Long lessonId) {
        LessonDto lesson = lessonService.getLessonById(lessonId);
        return ResponseEntity.ok(ApiResponseSchema.success(lesson, "수업 상세 조회 성공"));
    }

    @Operation(summary = "기간별 수업 조회", description = "특정 기간의 수업 목록을 조회합니다.")
    @GetMapping("/lessons/period")
    public ResponseEntity<ApiResponseSchema<List<LessonDto>>> getLessonsByPeriod(
            @Parameter(description = "시작 날짜 (YYYY-MM-DD)") @RequestParam LocalDate startDate,
            @Parameter(description = "종료 날짜 (YYYY-MM-DD)") @RequestParam LocalDate endDate) {
        List<LessonDto> lessons = lessonService.getLessonsByDateRange(startDate, endDate, "OPEN");
        return ResponseEntity.ok(ApiResponseSchema.success(lessons, "기간별 수업 조회 성공"));
    }

    // 2. 사물함 조회 API
    @Operation(summary = "사용 가능한 사물함 조회", description = "성별에 따라, 사용 가능한 사물함 목록을 조회합니다.")
    @GetMapping("/lockers")
    public ResponseEntity<ApiResponseSchema<List<LockerDto>>> getAvailableLockers(
            @Parameter(description = "성별 (M/F)") @RequestParam String gender) {
        List<LockerDto> lockers = lockerService.getAvailableLockers(gender);
        return ResponseEntity.ok(ApiResponseSchema.success(lockers, "사용 가능한 사물함 조회 성공"));
    }

    @Operation(summary = "특정 사물함 조회", description = "사물함 ID로 특정 사물함 정보를 조회합니다.")
    @GetMapping("/lockers/{lockerId}")
    public ResponseEntity<ApiResponseSchema<LockerDto>> getLockerDetail(
            @Parameter(description = "조회할 사물함 ID") @PathVariable Long lockerId) {
        LockerDto locker = lockerService.getLockerById(lockerId);
        return ResponseEntity.ok(ApiResponseSchema.success(locker, "사물함 상세 조회 성공"));
    }

    // 3. 신청 및 취소 API
    @Operation(summary = "수업 신청", description = "수업을 신청합니다. 결제는 마이페이지에서 진행됩니다.")
    @PostMapping("/enroll")
    public ResponseEntity<ApiResponseSchema<EnrollResponseDto>> createEnroll(
            @Valid @RequestBody EnrollRequestDto enrollRequest,
            Authentication authentication,
            HttpServletRequest request) {
        User currentUser = getAuthenticatedUser(authentication);
        String clientIp = request.getRemoteAddr();
        
        EnrollResponseDto enrollResponse = enrollmentService.createInitialEnrollment(currentUser, enrollRequest, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseSchema.success(enrollResponse, "수업 신청이 완료되었습니다. 마이페이지에서 결제를 진행해주세요."));
    }

    @Operation(summary = "신청 취소", description = "수업 신청을 취소합니다. 개강 전 신청 건에 한해 사용자 직접 취소가 가능합니다.")
    @PostMapping("/enroll/{enrollId}/cancel")
    public ResponseEntity<ApiResponseSchema<Void>> cancelEnroll(
            @Parameter(description = "취소할 신청 ID") @PathVariable Long enrollId,
            @Valid @RequestBody CancelRequestDto cancelRequest,
            Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        
        enrollmentService.requestEnrollmentCancellation(currentUser, enrollId, cancelRequest.getReason());
        
        return ResponseEntity.ok(ApiResponseSchema.success("신청 취소가 요청되었습니다."));
    }

    // 4. 신청 내역 조회 API
    @Operation(summary = "내 신청 내역 조회", description = "로그인한 사용자의 모든 신청 내역을 조회합니다. (Mypage DTO 사용)")
    @GetMapping("/my-enrolls")
    public ResponseEntity<ApiResponseSchema<Page<cms.mypage.dto.EnrollDto>>> getMyEnrolls(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        Page<cms.mypage.dto.EnrollDto> enrolls = enrollmentService.getEnrollments(currentUser, null, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(enrolls, "신청 내역 조회 성공"));
    }

    @Operation(summary = "내 신청 내역 상태별 조회", description = "로그인한 사용자의 신청 내역을 상태별로 조회합니다. (Mypage DTO 사용)")
    @GetMapping("/my-enrolls/status")
    public ResponseEntity<ApiResponseSchema<Page<cms.mypage.dto.EnrollDto>>> getMyEnrollsByStatus(
            @Parameter(description = "신청 상태 (e.g. UNPAID, PAID, CANCELED - pay_status field from Enroll)") @RequestParam String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        Page<cms.mypage.dto.EnrollDto> enrolls = enrollmentService.getEnrollments(currentUser, status, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(enrolls, "상태별 신청 내역 조회 성공"));
    }

    @Operation(summary = "특정 신청 상세 조회", description = "특정 신청의 상세 정보를 조회합니다. (Mypage DTO 사용)")
    @GetMapping("/enrolls/{enrollId}")
    public ResponseEntity<ApiResponseSchema<cms.mypage.dto.EnrollDto>> getEnrollDetail(
            @Parameter(description = "조회할 신청 ID") @PathVariable Long enrollId,
            Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        cms.mypage.dto.EnrollDto enroll = enrollmentService.getEnrollmentDetails(currentUser, enrollId);
        
        return ResponseEntity.ok(ApiResponseSchema.success(enroll, "신청 상세 조회 성공"));
    }

    // 보조 메소드
    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new IllegalStateException("로그인이 필요한 서비스입니다. 인증 정보가 유효하지 않습니다.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        } else if (principal instanceof UserDetails) {
            throw new IllegalStateException("인증 객체가 User 타입이 아닙니다. UserDetails 타입 변환 로직이 필요합니다: " + principal.getClass().getName());
        } else {
            throw new IllegalStateException("인증 객체 타입이 예상과 다릅니다: " + principal.getClass().getName());
        }
    }
} 