package cms.enterprise.service.impl;

import cms.enterprise.domain.EnterpriseDomain;
import cms.enterprise.dto.CreateEnterpriseRequest;
import cms.enterprise.dto.EnterpriseDto;
import cms.enterprise.dto.UpdateEnterpriseRequest;
import cms.enterprise.repository.EnterpriseRepository;
import cms.enterprise.service.EnterpriseService;
import cms.common.exception.DuplicateResourceException; // 예시 예외 클래스
import cms.common.exception.ResourceNotFoundException; // 예시 예외 클래스
import cms.file.entity.CmsFile;
import cms.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class EnterpriseServiceImpl implements EnterpriseService {

    private static final String ENTERPRISE_IMAGE_MENU_TYPE = "ENTERPRISE_IMAGE";

    private final EnterpriseRepository enterpriseRepository;
    private final FileService fileService;

    @Override
    @Transactional(readOnly = true)
    public Page<EnterpriseDto> getAllEnterprises(Integer year, String name, String representative, String businessType, Pageable pageable) {
        Page<EnterpriseDomain> enterprisesPage = enterpriseRepository.findByYearAndNameContainingFilters(
                year, name, representative, businessType, pageable);
        return enterprisesPage.map(this::convertToDto); // 엔티티를 DTO로 변환
    }

    @Override
    @Transactional(readOnly = true)
    public EnterpriseDto getEnterpriseById(Long id) {
        EnterpriseDomain enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enterprise not found with id: " + id));
        return convertToDto(enterprise);
    }

    @Override
    public EnterpriseDto createEnterprise(CreateEnterpriseRequest createRequest, MultipartFile imageFile, String createdBy, String createdIp) {
        // 중복 체크 (동일 연도, 동일 이름)
        enterpriseRepository.findByNameAndYear(createRequest.getName(), createRequest.getYear())
            .ifPresent(e -> {
                throw new DuplicateResourceException("Enterprise with name '" + createRequest.getName() + "' already exists in year " + createRequest.getYear());
            });

        // 먼저 기업 정보 저장 (이미지 없이)
        EnterpriseDomain enterprise = EnterpriseDomain.builder()
                .year(createRequest.getYear())
                .name(createRequest.getName())
                .description(createRequest.getDescription())
                .image(createRequest.getImage()) // 기본 이미지 경로가 있으면 설정
                .representative(createRequest.getRepresentative())
                .established(createRequest.getEstablished())
                .businessType(createRequest.getBusinessType())
                .detail(createRequest.getDetail())
                .showButton(createRequest.getShowButton() != null ? createRequest.getShowButton() : true)
                .createdBy(createdBy) // 감사 필드 설정
                .createdIp(createdIp)   // 감사 필드 설정
                .build();

        EnterpriseDomain savedEnterprise = enterpriseRepository.save(enterprise);

        // 이미지 파일이 제공되었으면 업로드 처리
        if (imageFile != null && !imageFile.isEmpty()) {
            String imagePath = processEnterpriseImage(savedEnterprise.getId(), imageFile);
            savedEnterprise.setImage(imagePath);
            savedEnterprise = enterpriseRepository.save(savedEnterprise);
        }

        return convertToDto(savedEnterprise);
    }

    @Override
    public EnterpriseDto updateEnterprise(Long id, UpdateEnterpriseRequest updateRequest, MultipartFile imageFile, String updatedBy, String updatedIp) {
        EnterpriseDomain enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enterprise not found with id: " + id));

        // 이름과 연도가 변경되는 경우에만 중복 체크
        String checkName = updateRequest.getName() != null ? updateRequest.getName() : enterprise.getName();
        Integer checkYear = updateRequest.getYear() != null ? updateRequest.getYear() : enterprise.getYear();

        if ((updateRequest.getName() != null && !enterprise.getName().equals(updateRequest.getName())) || 
            (updateRequest.getYear() != null && !enterprise.getYear().equals(updateRequest.getYear()))) {
            enterpriseRepository.findByNameAndYear(checkName, checkYear)
                .filter(e -> !e.getId().equals(id)) // 자기 자신은 제외
                .ifPresent(e -> {
                    throw new DuplicateResourceException("Enterprise with name '" + checkName + "' already exists in year " + checkYear);
                });
        }

        // 기본 필드 업데이트
        updateEnterpriseFields(enterprise, updateRequest);
        
        // 이미지 파일이 제공되었으면 업로드 처리 및 경로 업데이트
        if (imageFile != null && !imageFile.isEmpty()) {
            // 기존 이미지가 있으면 관련 파일 삭제 처리 (필요시)
            if (StringUtils.hasText(enterprise.getImage())) {
                deleteOldEnterpriseImage(id);
            }
            
            String imagePath = processEnterpriseImage(id, imageFile);
            enterprise.setImage(imagePath);
        }
        
        enterprise.setUpdatedBy(updatedBy); // 감사 필드 설정
        enterprise.setUpdatedIp(updatedIp);   // 감사 필드 설정

        EnterpriseDomain updatedEnterprise = enterpriseRepository.save(enterprise);
        return convertToDto(updatedEnterprise);
    }

    @Override
    public void deleteEnterprise(Long id) {
        if (!enterpriseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Enterprise not found with id: " + id);
        }
        
        // 관련 이미지 파일 삭제
        deleteOldEnterpriseImage(id);
        
        // 기업 정보 삭제
        enterpriseRepository.deleteById(id);
    }

    /**
     * 입주기업 이미지를 처리하고 이미지 경로를 반환합니다.
     * 
     * @param enterpriseId 기업 ID
     * @param imageFile 이미지 파일
     * @return 저장된 이미지 경로
     */
    private String processEnterpriseImage(Long enterpriseId, MultipartFile imageFile) {
        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return null;
            }
            
            // 파일 서비스를 사용하여 이미지 파일 업로드
            List<MultipartFile> files = Collections.singletonList(imageFile);
            List<CmsFile> uploadedFiles = fileService.uploadFiles(ENTERPRISE_IMAGE_MENU_TYPE, enterpriseId, files);
            
            if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
                CmsFile uploadedFile = uploadedFiles.get(0);
                log.debug("입주기업 이미지 업로드 완료: fileId={}, fileName={}", uploadedFile.getFileId(), uploadedFile.getOriginName());
                
                // API 기본 경로 + 파일 다운로드 URL 형식으로 반환
                return "/api/v1/cms/file/public/view/" + uploadedFile.getFileId();
            }
        } catch (Exception e) {
            log.error("입주기업 이미지 처리 중 오류 발생: {}", e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 기존 입주기업 이미지 파일을 삭제합니다.
     * 
     * @param enterpriseId 기업 ID
     */
    private void deleteOldEnterpriseImage(Long enterpriseId) {
        try {
            // 기업 ID와 연결된 이미지 파일 목록 조회
            List<CmsFile> existingFiles = fileService.getList(ENTERPRISE_IMAGE_MENU_TYPE, enterpriseId, null);
            
            // 파일이 있으면 삭제
            if (existingFiles != null && !existingFiles.isEmpty()) {
                for (CmsFile file : existingFiles) {
                    fileService.deleteFile(file.getFileId());
                    log.debug("입주기업 이미지 삭제 완료: fileId={}, fileName={}", file.getFileId(), file.getOriginName());
                }
            }
        } catch (Exception e) {
            log.error("입주기업 이미지 삭제 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    // 엔티티를 DTO로 변환하는 헬퍼 메소드
    private EnterpriseDto convertToDto(EnterpriseDomain enterprise) {
        // ModelMapper 사용 시: return modelMapper.map(enterprise, EnterpriseDto.class);
        return EnterpriseDto.builder()
                .id(enterprise.getId())
                .year(enterprise.getYear())
                .name(enterprise.getName())
                .description(enterprise.getDescription())
                .image(enterprise.getImage())
                .representative(enterprise.getRepresentative())
                .established(enterprise.getEstablished())
                .businessType(enterprise.getBusinessType())
                .detail(enterprise.getDetail())
                .showButton(enterprise.getShowButton())
                .createdAt(enterprise.getCreatedAt())
                .updatedAt(enterprise.getUpdatedAt())
                .createdBy(enterprise.getCreatedBy()) // 감사 필드 매핑
                .updatedBy(enterprise.getUpdatedBy())   // 감사 필드 매핑
                .build();
    }

    // UpdateEnterpriseRequest의 non-null 필드를 엔티티에 반영하는 헬퍼 메소드
    private void updateEnterpriseFields(EnterpriseDomain enterprise, UpdateEnterpriseRequest dto) {
        if (dto.getYear() != null) {
            enterprise.setYear(dto.getYear());
        }
        if (dto.getName() != null) {
            enterprise.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            enterprise.setDescription(dto.getDescription());
        }
        if (dto.getImage() != null) {
            enterprise.setImage(dto.getImage());
        }
        if (dto.getRepresentative() != null) {
            enterprise.setRepresentative(dto.getRepresentative());
        }
        if (dto.getEstablished() != null) {
            enterprise.setEstablished(dto.getEstablished());
        }
        if (dto.getBusinessType() != null) {
            enterprise.setBusinessType(dto.getBusinessType());
        }
        if (dto.getDetail() != null) {
            enterprise.setDetail(dto.getDetail());
        }
        if (dto.getShowButton() != null) {
            enterprise.setShowButton(dto.getShowButton());
        }
    }
} 