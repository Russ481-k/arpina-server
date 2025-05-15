package cms.enterprise.service;

import cms.enterprise.dto.CreateEnterpriseRequest;
import cms.enterprise.dto.EnterpriseDto;
import cms.enterprise.dto.UpdateEnterpriseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface EnterpriseService {

    /**
     * 조건에 따라 입주 기업 목록을 조회합니다. (페이지네이션 포함)
     *
     * @param year 검색할 연도 (optional)
     * @param name 검색할 기업명 (optional, partial match)
     * @param representative 검색할 대표자명 (optional, partial match)
     * @param businessType 검색할 업종 (optional, partial match)
     * @param pageable 페이지네이션 및 정렬 정보
     * @return 페이징 처리된 기업 DTO 목록
     */
    Page<EnterpriseDto> getAllEnterprises(Integer year, String name, String representative, String businessType, Pageable pageable);

    /**
     * ID로 특정 입주 기업 정보를 조회합니다.
     *
     * @param id 조회할 기업의 ID
     * @return 기업 DTO
     * @throws cms.common.exception.ResourceNotFoundException 해당 ID의 기업이 없을 경우
     */
    EnterpriseDto getEnterpriseById(Long id);

    /**
     * 새로운 입주 기업 정보를 생성합니다.
     *
     * @param createRequest 생성 요청 DTO
     * @param imageFile 기업 이미지 파일 (optional)
     * @param createdBy 생성자 ID (감사 정보용, 필요시 UserDetails 등에서 추출)
     * @param createdIp 생성자 IP (감사 정보용, 필요시 HttpServletRequest 등에서 추출)
     * @return 생성된 기업 DTO
     * @throws cms.common.exception.DuplicateResourceException 동일 연도에 동일 기업명 존재 시
     */
    EnterpriseDto createEnterprise(CreateEnterpriseRequest createRequest, MultipartFile imageFile, String createdBy, String createdIp);

    /**
     * 기존 입주 기업 정보를 수정합니다.
     *
     * @param id 수정할 기업의 ID
     * @param updateRequest 수정 요청 DTO
     * @param imageFile 기업 이미지 파일 (optional)
     * @param updatedBy 수정자 ID (감사 정보용)
     * @param updatedIp 수정자 IP (감사 정보용)
     * @return 수정된 기업 DTO
     * @throws cms.common.exception.ResourceNotFoundException 해당 ID의 기업이 없을 경우
     * @throws cms.common.exception.DuplicateResourceException 수정 시 다른 기업과 동일 연도/기업명 충돌 시
     */
    EnterpriseDto updateEnterprise(Long id, UpdateEnterpriseRequest updateRequest, MultipartFile imageFile, String updatedBy, String updatedIp);

    /**
     * 특정 입주 기업 정보를 삭제합니다.
     *
     * @param id 삭제할 기업의 ID
     * @throws cms.common.exception.ResourceNotFoundException 해당 ID의 기업이 없을 경우
     */
    void deleteEnterprise(Long id);
} 