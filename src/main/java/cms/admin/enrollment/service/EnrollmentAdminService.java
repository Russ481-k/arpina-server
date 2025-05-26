package cms.admin.enrollment.service;

import cms.admin.enrollment.dto.EnrollAdminResponseDto;
import cms.admin.enrollment.model.dto.TemporaryEnrollmentRequestDto;
import cms.admin.enrollment.dto.CancelRequestAdminDto;
import cms.admin.enrollment.dto.DiscountStatusUpdateRequestDto;
import cms.admin.enrollment.dto.CalculatedRefundDetailsDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import cms.enroll.domain.Enroll;

public interface EnrollmentAdminService {
    Page<EnrollAdminResponseDto> getAllEnrollments(Integer year, Integer month, Long lessonId, String userId, String payStatus, Pageable pageable);
    EnrollAdminResponseDto getEnrollmentById(Long enrollId);
    Page<CancelRequestAdminDto> getCancelRequests(Long lessonId, List<Enroll.CancelStatusType> cancelStatuses, List<String> targetPayStatuses, boolean useCombinedLogic, Pageable pageable);
    EnrollAdminResponseDto approveCancellationWithManualDays(Long enrollId, String adminComment, Integer manualUsedDays);
    EnrollAdminResponseDto denyCancellation(Long enrollId, String adminComment);
    EnrollAdminResponseDto adminCancelEnrollment(Long enrollId, String adminComment);
    EnrollAdminResponseDto updateEnrollmentDiscountStatus(Long enrollId, DiscountStatusUpdateRequestDto request);
    CalculatedRefundDetailsDto getRefundPreview(Long enrollId, Integer manualUsedDays);
    EnrollAdminResponseDto createTemporaryEnrollment(TemporaryEnrollmentRequestDto requestDto);
} 