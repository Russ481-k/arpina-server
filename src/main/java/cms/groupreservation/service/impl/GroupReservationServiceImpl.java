package cms.groupreservation.service.impl;

import cms.common.exception.ResourceNotFoundException;
import cms.groupreservation.domain.GroupReservationInquiry;
import cms.groupreservation.domain.InquiryRoomReservation;
import cms.groupreservation.dto.GroupReservationInquiryDto;
import cms.groupreservation.dto.GroupReservationRequest;
import cms.groupreservation.repository.GroupReservationInquiryRepository;
import cms.groupreservation.service.GroupReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupReservationServiceImpl implements GroupReservationService {

    private final GroupReservationInquiryRepository inquiryRepository;

    @Override
    @Transactional
    public Long createInquiry(GroupReservationRequest request, HttpServletRequest servletRequest) {
        String clientIp = servletRequest.getRemoteAddr();

        GroupReservationInquiry inquiry = GroupReservationInquiry.builder()
                .status("PENDING")
                .eventType(request.getEventType())
                .eventName(request.getEventName())
                .seatingArrangement(request.getSeatingArrangement())
                .adultAttendees(request.getAdultAttendees())
                .childAttendees(request.getChildAttendees())
                .diningServiceUsage(request.getDiningServiceUsage())
                .otherRequests(request.getOtherRequests())
                .customerGroupName(request.getCustomerGroupName())
                .customerRegion(request.getCustomerRegion())
                .contactPersonName(request.getContactPersonName())
                .contactPersonDpt(request.getContactPersonDpt())
                .contactPersonPhone(request.getContactPersonPhone())
                .contactPersonEmail(request.getContactPersonEmail())
                .privacyAgreed(request.getPrivacyAgreed())
                .marketingAgreed(request.getMarketingAgreed())
                .createdBy("GUEST")
                .createdIp(clientIp)
                .build();

        List<InquiryRoomReservation> roomReservations = request.getRoomReservations().stream()
                .map(roomRequest -> InquiryRoomReservation.builder()
                        .roomSizeDesc(roomRequest.getRoomSizeDesc())
                        .roomTypeDesc(roomRequest.getRoomTypeDesc())
                        .startDate(roomRequest.getStartDate())
                        .endDate(roomRequest.getEndDate())
                        .usageTimeDesc(roomRequest.getUsageTimeDesc())
                        .createdBy("GUEST")
                        .createdIp(clientIp)
                        .inquiry(inquiry)
                        .build())
                .collect(Collectors.toList());

        inquiry.setRoomReservations(roomReservations);

        GroupReservationInquiry savedInquiry = inquiryRepository.save(inquiry);
        return savedInquiry.getId();
    }

    @Override
    public Page<GroupReservationInquiryDto> getInquiries(Pageable pageable) {
        return inquiryRepository.findAll(pageable).map(GroupReservationInquiryDto::new);
    }

    @Override
    public GroupReservationInquiryDto getInquiry(Long id) {
        return inquiryRepository.findById(id)
                .map(GroupReservationInquiryDto::new)
                .orElseThrow(() -> new ResourceNotFoundException("GroupReservationInquiry", id));
    }

    @Override
    @Transactional
    public GroupReservationInquiryDto updateInquiry(Long id, String status, String adminMemo,
            HttpServletRequest servletRequest) {
        GroupReservationInquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GroupReservationInquiry", id));

        String adminUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        String clientIp = servletRequest.getRemoteAddr();

        if (status != null) {
            inquiry.setStatus(status);
        }
        if (adminMemo != null) {
            inquiry.setAdminMemo(adminMemo);
        }
        inquiry.setUpdatedBy(adminUsername);
        inquiry.setUpdatedIp(clientIp);

        return new GroupReservationInquiryDto(inquiryRepository.save(inquiry));
    }
}