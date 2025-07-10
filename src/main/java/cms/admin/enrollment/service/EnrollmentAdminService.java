package cms.admin.enrollment.service;

import cms.admin.enrollment.dto.EnrollAdminResponseDto;
import cms.admin.enrollment.model.dto.TemporaryEnrollmentRequestDto;
import cms.admin.enrollment.dto.CancelRequestAdminDto;
import cms.admin.enrollment.dto.DiscountStatusUpdateRequestDto;
import cms.admin.enrollment.dto.CalculatedRefundDetailsDto;
import cms.admin.enrollment.dto.AdminCancelRequestDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import cms.enroll.domain.Enroll;

public interface EnrollmentAdminService {
        Page<EnrollAdminResponseDto> getAllEnrollments(Integer year, Integer month, Long lessonId, String userId,
                        String payStatus, Pageable pageable);

        EnrollAdminResponseDto getEnrollmentById(Long enrollId);

        Page<CancelRequestAdminDto> getCancelRequests(Long lessonId, List<Enroll.CancelStatusType> cancelStatuses,
                        List<String> targetPayStatuses, boolean useCombinedLogic, Pageable pageable);

        /**
         * 관리자가 수강 신청 취소 요청을 승인합니다.
         * 
         * @param enrollId         신청 ID
         * @param cancelRequestDto 관리자가 직접 입력한 환불 정보 DTO
         */
        void approveCancellation(Long enrollId, AdminCancelRequestDto cancelRequestDto);

        EnrollAdminResponseDto denyCancellation(Long enrollId, String adminComment);

        EnrollAdminResponseDto adminCancelEnrollment(Long enrollId, String adminComment);

        EnrollAdminResponseDto updateEnrollmentDiscountStatus(Long enrollId, DiscountStatusUpdateRequestDto request);

        EnrollAdminResponseDto updateLockerNo(Long enrollId, String lockerNo);

        CalculatedRefundDetailsDto getRefundPreview(Long enrollId, Integer manualUsedDays);

        EnrollAdminResponseDto createTemporaryEnrollment(TemporaryEnrollmentRequestDto requestDto);

        EnrollAdminResponseDto changeLesson(Long enrollmentId, Long newLessonId);
}