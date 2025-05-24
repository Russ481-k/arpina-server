package cms.admin.enrollment.service;

import cms.admin.enrollment.dto.EnrollAdminResponseDto;
import cms.admin.enrollment.dto.CancelRequestAdminDto;
import cms.admin.enrollment.dto.DiscountStatusUpdateRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EnrollmentAdminService {
    Page<EnrollAdminResponseDto> getAllEnrollments(Integer year, Integer month, Long lessonId, String userId, String payStatus, Pageable pageable);
    EnrollAdminResponseDto getEnrollmentById(Long enrollId);
    Page<CancelRequestAdminDto> getCancelRequests(String status, Pageable pageable); // status typically "REQ"
    EnrollAdminResponseDto approveCancellationWithManualDays(Long enrollId, String adminComment, Integer manualUsedDays);
    EnrollAdminResponseDto denyCancellation(Long enrollId, String adminComment);
    EnrollAdminResponseDto adminCancelEnrollment(Long enrollId, String adminComment);
    EnrollAdminResponseDto updateEnrollmentDiscountStatus(Long enrollId, DiscountStatusUpdateRequestDto request);
} 