package cms.groupreservation.service;

import cms.groupreservation.dto.GroupReservationInquiryDto;
import cms.groupreservation.dto.GroupReservationRequest;
import cms.groupreservation.dto.GroupReservationUpdateRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GroupReservationService {

        /**
         * 공개 단체 예약 문의 등록
         * 
         * @param request        DTO
         * @param servletRequest HTTP 요청
         * @return 생성된 문의 ID
         */
        Long createInquiry(GroupReservationRequest request);

        /**
         * 관리자용 문의 목록 조회
         * 
         * @param pageable 페이징 정보
         * @return 문의 목록
         */
        Page<GroupReservationInquiryDto> getInquiries(Pageable pageable, String type, String search,
                        String status, String eventType);

        /**
         * 관리자용 문의 상세 조회
         * 
         * @param id 문의 ID
         * @return 문의 상세 정보
         */
        GroupReservationInquiryDto getInquiry(Long id);

        /**
         * 관리자용 문의 상태 및 메모 업데이트
         * 
         * @param id             문의 ID
         * @param requestDto     관리자 업데이트 요청 DTO
         * @param servletRequest HTTP 요청
         * @return 업데이트된 문의 정보
         */
        GroupReservationInquiryDto updateInquiry(Long id, GroupReservationUpdateRequestDto requestDto);
}