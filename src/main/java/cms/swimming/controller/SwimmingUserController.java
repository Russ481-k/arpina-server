package cms.swimming.controller;

import cms.common.dto.ApiResponseSchema;
import cms.swimming.dto.CancelRequestDto;
import cms.swimming.dto.CheckEnrollmentEligibilityDto;
import cms.swimming.dto.EnrollRequestDto;
import cms.swimming.dto.EnrollResponseDto;
import cms.swimming.dto.LessonDto;
import cms.enroll.service.EnrollmentService;
import cms.swimming.service.LessonService;
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
import cms.common.util.IpUtil;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/swimming")

@Tag(name = "Swimming User API", description = "수영장 사용자 API (수업 조회, 신청 관련)") // Updated tag description
@Validated
public class SwimmingUserController {

    private final LessonService lessonService;
    // private final LockerService lockerService; // lockerService 주입 삭제 (만약 다른 곳에서
    // 사용하지 않는다면)
    private final EnrollmentService enrollmentService;

    // 1. 수업 조회 API
    @Operation(summary = "수업 목록 조회", description = "다양한 조건(상태, 연도, 월, 기간)으로 필터링된 수업 목록을 페이징하여 제공합니다. 월은 여러 개를 콤마로 구분하여 전달할 수 있습니다 (예: month=5,6).")
    @GetMapping("/lessons")
    public ResponseEntity<ApiResponseSchema<Page<LessonDto>>> getLessons(
            @RequestParam(required = false) String status,
            @Parameter(description = "조회할 월 (여러 개 가능, 콤마로 구분)") @RequestParam(required = false) List<Integer> months,
            @Parameter(description = "조회 시작 날짜 (YYYY-MM-DD)") @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "조회 종료 날짜 (YYYY-MM-DD)") @RequestParam(required = false) LocalDate endDate,
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<LessonDto> lessons = lessonService.getLessons(months, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(lessons, "수업 목록 조회 성공"));
    }

    @Operation(summary = "특정 수업 상세 조회", description = "특정 수업의 상세 정보를 조회합니다.")
    @GetMapping("/lessons/{lessonId}")
    public ResponseEntity<ApiResponseSchema<LessonDto>> getLessonDetail(
            @Parameter(description = "조회할 수업 ID") @PathVariable Long lessonId) {
        LessonDto lesson = lessonService.getLessonById(lessonId);
        return ResponseEntity.ok(ApiResponseSchema.success(lesson, "수업 상세 조회 성공"));
    }

    // 3. 신청 및 취소 API
    @Operation(summary = "수업 신청", description = "수업을 신청하고 결제합합니다.")
    @PostMapping("/enroll")
    public ResponseEntity<ApiResponseSchema<EnrollResponseDto>> createEnroll(
            @Valid @RequestBody EnrollRequestDto enrollRequest,
            Authentication authentication,
            HttpServletRequest request) {
        User currentUser = getAuthenticatedUser(authentication);
        String clientIp = IpUtil.getClientIp();

        EnrollResponseDto enrollResponse = enrollmentService.createInitialEnrollment(currentUser, enrollRequest,
                clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseSchema.success(enrollResponse, "수업 신청 및 결제가 완료되었습니다."));
    }

    @Operation(summary = "신청 취소", description = "수업 신청을 취소합니다. 개강 전 신청 건에 한해 사용자 직접 취소가 가능합니다.")
    @PostMapping("/enroll/{enrollId}/cancel")
    public ResponseEntity<ApiResponseSchema<Void>> cancelEnroll(
            @Parameter(description = "취소할 신청 ID", required = true) @PathVariable Long enrollId,
            @Valid @RequestBody CancelRequestDto cancelRequest,
            Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);

        enrollmentService.requestEnrollmentCancellation(currentUser, enrollId, cancelRequest.getReason());

        return ResponseEntity.ok(ApiResponseSchema.success("신청 취소가 요청되었습니다."));
    }

    // 4. 신청 내역 조회 API
    @Operation(summary = "내 신청 내역 조회", description = "로그인한 사용자의 신청 내역을 상태별(선택) 또는 전체 조회합니다. (Mypage DTO 사용)")
    @GetMapping("/enrolls")
    public ResponseEntity<ApiResponseSchema<Page<cms.mypage.dto.EnrollDto>>> getMyEnrolls(
            @Parameter(description = "신청 상태 (선택적 필터, 예: UNPAID, PAID, CANCELED - pay_status field from Enroll)") @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        Page<cms.mypage.dto.EnrollDto> enrolls = enrollmentService.getEnrollments(currentUser, status, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(enrolls, "신청 내역 조회 성공"));
    }

    @Operation(summary = "특정 신청 상세 조회", description = "특정 신청의 상세 정보를 조회합니다. (Mypage DTO 사용)")
    @GetMapping("/enroll/{enrollId}")
    public ResponseEntity<ApiResponseSchema<cms.mypage.dto.EnrollDto>> getEnrollDetail(
            @Parameter(description = "조회할 신청 ID", required = true) @PathVariable Long enrollId,
            Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        cms.mypage.dto.EnrollDto enroll = enrollmentService.getEnrollmentDetails(currentUser, enrollId);

        return ResponseEntity.ok(ApiResponseSchema.success(enroll, "신청 상세 조회 성공"));
    }

    @Operation(summary = "수강 신청 자격 확인", description = "사용자가 특정 강습에 대해 해당 월에 중복 신청하는 것이 아닌지 확인합니다.")
    @GetMapping("/enroll/eligibility")
    public ResponseEntity<ApiResponseSchema<CheckEnrollmentEligibilityDto>> checkEnrollmentEligibility(
            @Parameter(description = "확인할 강습 ID", required = true) @RequestParam Long lessonId,
            Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        CheckEnrollmentEligibilityDto eligibility = enrollmentService.checkEnrollmentEligibility(currentUser, lessonId);
        return ResponseEntity.ok(ApiResponseSchema.success(eligibility, "수강 신청 가능 여부 조회 성공"));
    }

    // 5. 보조 메소드
    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new IllegalStateException("로그인이 필요한 서비스입니다. 인증 정보가 유효하지 않습니다.");
        }
        Object principal = authentication.getPrincipal();

        if (principal instanceof User) {
            return (User) principal;
        } else if (principal instanceof cms.user.dto.CustomUserDetails) { // Check for CustomUserDetails
            return ((cms.user.dto.CustomUserDetails) principal).getUser(); // Get User from CustomUserDetails
        } else if (principal instanceof UserDetails) {
            // This case might still be problematic if a UserDetails implementation other
            // than
            // CustomUserDetails is used and doesn't directly provide the full User entity.
            // For now, we log a warning and attempt to get username for potential further
            // lookup,
            // but ideally, the system should consistently use CustomUserDetails or a
            // similar pattern.
            // String username = ((UserDetails) principal).getUsername();
            // logger.warn("Principal is UserDetails but not CustomUserDetails. Username:
            // {}. Full User entity might be missing.", username);
            // Consider fetching user from repository:
            // userRepository.findByUsername(username).orElseThrow...
            // This would require UserRepository to be injected into the controller.
            throw new IllegalStateException(
                    "인증 객체가 User 또는 CustomUserDetails 타입이 아닙니다. 확인이 필요합니다: " + principal.getClass().getName());
        } else {
            throw new IllegalStateException("인증 객체 타입이 예상과 다릅니다: " + principal.getClass().getName());
        }
    }
}