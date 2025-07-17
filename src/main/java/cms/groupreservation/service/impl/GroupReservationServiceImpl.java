package cms.groupreservation.service.impl;

import cms.common.exception.ResourceNotFoundException;
import cms.groupreservation.domain.GroupReservationInquiry;
import cms.groupreservation.domain.InquiryRoomReservation;
import cms.groupreservation.dto.GroupReservationInquiryDto;
import cms.groupreservation.dto.GroupReservationRequest;
import cms.groupreservation.dto.GroupReservationUpdateRequestDto;
import cms.groupreservation.repository.GroupReservationInquiryRepository;
import cms.groupreservation.service.GroupReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import cms.common.util.IpUtil;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupReservationServiceImpl implements GroupReservationService {

    private final GroupReservationInquiryRepository inquiryRepository;

    @Override
    @Transactional
    public Long createInquiry(GroupReservationRequest request) {
        String clientIp = IpUtil.getClientIp();

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
                .contactPersonTel(request.getContactPersonTel())
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
    public Page<GroupReservationInquiryDto> getInquiries(Pageable pageable, String type, String search,
            String status, String eventType) {
        Specification<GroupReservationInquiry> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(eventType)) {
                predicates.add(criteriaBuilder.equal(root.get("eventType"), eventType));
            }

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search + "%";
                if ("ALL".equalsIgnoreCase(type) || !StringUtils.hasText(type)) {
                    predicates.add(criteriaBuilder.or(
                            criteriaBuilder.like(root.get("eventName"), pattern),
                            criteriaBuilder.like(root.get("customerGroupName"), pattern),
                            criteriaBuilder.like(root.get("contactPersonName"), pattern),
                            criteriaBuilder.like(root.get("contactPersonPhone"), pattern),
                            criteriaBuilder.like(root.get("contactPersonTel"), pattern)));
                } else {
                    switch (type) {
                        case "eventName":
                            predicates.add(criteriaBuilder.like(root.get("eventName"), pattern));
                            break;
                        case "customerGroupName":
                            predicates.add(criteriaBuilder.like(root.get("customerGroupName"), pattern));
                            break;
                        case "contactPersonName":
                            predicates.add(criteriaBuilder.like(root.get("contactPersonName"), pattern));
                            break;
                        case "contactPersonPhone":
                            predicates.add(criteriaBuilder.like(root.get("contactPersonPhone"), pattern));
                            break;
                        case "contactPersonTel":
                            predicates.add(criteriaBuilder.like(root.get("contactPersonTel"), pattern));
                            break;
                    }
                }
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return inquiryRepository.findAll(spec, pageable).map(GroupReservationInquiryDto::new);
    }

    @Override
    public GroupReservationInquiryDto getInquiry(Long id) {
        return inquiryRepository.findById(id)
                .map(GroupReservationInquiryDto::new)
                .orElseThrow(() -> new ResourceNotFoundException("GroupReservationInquiry", id));
    }

    @Override
    @Transactional
    public GroupReservationInquiryDto updateInquiry(Long id, GroupReservationUpdateRequestDto requestDto) {
        GroupReservationInquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GroupReservationInquiry", id));

        String adminUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        String clientIp = IpUtil.getClientIp();

        if (requestDto.getStatus() != null) {
            inquiry.setStatus(requestDto.getStatus());
        }
        if (requestDto.getMemo() != null) {
            inquiry.setAdminMemo(requestDto.getMemo());
        }
        inquiry.setUpdatedBy(adminUsername);
        inquiry.setUpdatedIp(clientIp);

        return new GroupReservationInquiryDto(inquiryRepository.save(inquiry));
    }
}