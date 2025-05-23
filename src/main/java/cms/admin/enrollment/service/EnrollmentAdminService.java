package cms.admin.enrollment.service;

import cms.admin.enrollment.dto.EnrollAdminResponseDto;
import cms.admin.enrollment.dto.CancelRequestAdminDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EnrollmentAdminService {
    Page<EnrollAdminResponseDto> getAllEnrollments(Long lessonId, String userId, String payStatus, Pageable pageable);
    EnrollAdminResponseDto getEnrollmentById(Long enrollId);
    Page<CancelRequestAdminDto> getCancelRequests(String status, Pageable pageable); // status typically "REQ"
    EnrollAdminResponseDto approveCancellation(Long enrollId, String adminComment);
    EnrollAdminResponseDto denyCancellation(Long enrollId, String adminComment);
} 