package cms.template.service.impl;

import cms.template.domain.Template;
import cms.template.dto.TemplateDto;
import cms.template.repository.TemplateRepository;
import cms.template.service.TemplateService;
import cms.common.dto.ApiResponseSchema;
import cms.template.exception.TemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateServiceImpl.class);
    private final TemplateRepository templateRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional(readOnly = true)
    public ApiResponseSchema<Page<TemplateDto>> getTemplates(String keyword, Pageable pageable) {
        try {
            logger.debug("템플릿 목록 조회 시작 - keyword: {}", keyword);
            Page<Template> templates = templateRepository.findByKeyword(keyword, pageable);
            logger.debug("템플릿 목록 조회 완료 - 총 {}건", templates.getTotalElements());
            return ApiResponseSchema.success(templates.map(this::convertToDto), "템플릿 목록이 성공적으로 조회되었습니다.");
        } catch (Exception e) {
            logger.error("템플릿 목록 조회 실패 - keyword: {}, error: {}", keyword, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseSchema<Page<TemplateDto>> getPublicTemplates(String keyword, Pageable pageable) {
        try {
            logger.debug("공개 템플릿 목록 조회 시작 - keyword: {}", keyword);
            Page<Template> templates = templateRepository.findByPublishedTrue(keyword, pageable);
            logger.debug("공개 템플릿 목록 조회 완료 - 총 {}건", templates.getTotalElements());
            return ApiResponseSchema.success(templates.map(this::convertToDto), "공개 템플릿 목록이 성공적으로 조회되었습니다.");
        } catch (Exception e) {
            logger.error("공개 템플릿 목록 조회 실패 - keyword: {}, error: {}", keyword, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseSchema<TemplateDto> getPublicTemplate(Long templateId) {
        try {
            logger.debug("공개 템플릿 상세 조회 시작 - templateId: {}", templateId);
            return templateRepository.findByTemplateIdAndPublished(templateId, true)
                    .map(template -> {
                        logger.debug("공개 템플릿 상세 조회 완료 - templateId: {}", templateId);
                        return ApiResponseSchema.success(convertToDto(template), "공개 템플릿이 성공적으로 조회되었습니다.");
                    })
                    .orElseThrow(() -> new TemplateNotFoundException(templateId));
        } catch (Exception e) {
            logger.error("공개 템플릿 상세 조회 실패 - templateId: {}, error: {}", templateId, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public ApiResponseSchema<TemplateDto> createTemplate(TemplateDto templateDto) {
        try {
            logger.debug("템플릿 생성 시작 - templateName: {}", templateDto.getTemplateName());
            Template template = Template.builder()
                    .templateName(templateDto.getTemplateName())
                    .description(templateDto.getDescription())
                    .build();
            Template savedTemplate = templateRepository.save(template);
            logger.debug("템플릿 생성 완료 - templateId: {}", savedTemplate.getTemplateId());
            return ApiResponseSchema.success(convertToDto(savedTemplate), "템플릿이 성공적으로 생성되었습니다.");
        } catch (Exception e) {
            logger.error("템플릿 생성 실패 - templateName: {}, error: {}", templateDto.getTemplateName(), e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseSchema<TemplateDto> getTemplate(Long templateId) {
        try {
            logger.debug("템플릿 상세 조회 시작 - templateId: {}", templateId);
            return templateRepository.findByTemplateIdAndPublished(templateId, true)
                    .map(template -> {
                        logger.debug("템플릿 상세 조회 완료 - templateId: {}", templateId);
                        return ApiResponseSchema.success(convertToDto(template), "템플릿이 성공적으로 조회되었습니다.");
                    })
                    .orElseThrow(() -> new TemplateNotFoundException(templateId));
        } catch (Exception e) {
            logger.error("템플릿 상세 조회 실패 - templateId: {}, error: {}", templateId, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public ApiResponseSchema<TemplateDto> updateTemplate(Long templateId, TemplateDto templateDto) {
        try {
            logger.debug("템플릿 수정 시작 - templateId: {}", templateId);
            Template template = findTemplateById(templateId);
            checkTemplateAccess(template);
            template.update(templateDto.getTemplateName(), templateDto.getDescription(), template.getLayoutJson());
            logger.debug("템플릿 수정 완료 - templateId: {}", templateId);
            return ApiResponseSchema.success(convertToDto(template), "템플릿이 성공적으로 수정되었습니다.");
        } catch (Exception e) {
            logger.error("템플릿 수정 실패 - templateId: {}, error: {}", templateId, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public ApiResponseSchema<Void> deleteTemplate(Long templateId) {
        try {
            logger.debug("템플릿 삭제 시작 - templateId: {}", templateId);
            Template template = findTemplateById(templateId);
            checkTemplateAccess(template);
            template.delete();
            templateRepository.save(template);
            logger.debug("템플릿 삭제 완료 - templateId: {}", templateId);
            return ApiResponseSchema.success("템플릿이 성공적으로 삭제되었습니다.");
        } catch (Exception e) {
            logger.error("템플릿 삭제 실패 - templateId: {}, error: {}", templateId, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public ApiResponseSchema<TemplateDto> cloneTemplate(Long templateId) {
        try {
            logger.debug("템플릿 복제 시작 - templateId: {}", templateId);
            Template original = findTemplateById(templateId);
            checkTemplateAccess(original);
            Template clone = Template.builder()
                    .templateName(original.getTemplateName() + " (복사본)")
                    .description(original.getDescription())
                    .build();
            Template savedClone = templateRepository.save(clone);
            logger.debug("템플릿 복제 완료 - originalTemplateId: {}, cloneTemplateId: {}", templateId, savedClone.getTemplateId());
            return ApiResponseSchema.success(convertToDto(savedClone), "템플릿이 성공적으로 복제되었습니다.");
        } catch (Exception e) {
            logger.error("템플릿 복제 실패 - templateId: {}, error: {}", templateId, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public ApiResponseSchema<TemplateDto> rollbackTemplate(Long templateId, Long versionId) {
        try {
            logger.debug("템플릿 롤백 시작 - templateId: {}, versionId: {}", templateId, versionId);
            Template template = findTemplateById(templateId);
            checkTemplateAccess(template);
            // TODO: 버전 롤백 로직 구현
            logger.debug("템플릿 롤백 완료 - templateId: {}, versionId: {}", templateId, versionId);
            return ApiResponseSchema.success(convertToDto(template), "템플릿이 성공적으로 롤백되었습니다.");
        } catch (Exception e) {
            logger.error("템플릿 롤백 실패 - templateId: {}, versionId: {}, error: {}", templateId, versionId, e.getMessage());
            throw e;
        }
    }

    private Template findTemplateById(Long templateId) {
        return templateRepository.findByTemplateIdAndPublished(templateId, true)
                .orElseThrow(() -> new TemplateNotFoundException(templateId));
    }

    private void checkTemplateAccess(Template template) {
        // TODO: 권한 체크 로직 구현
        // if (!hasPermission(template)) {
        //     throw new TemplateAccessDeniedException(template.getTemplateId());
        // }
    }

    private TemplateDto convertToDto(Template template) {
        return modelMapper.map(template, TemplateDto.class);
    }
} 
