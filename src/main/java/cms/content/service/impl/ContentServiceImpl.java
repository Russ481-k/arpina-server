package cms.content.service.impl;

import cms.content.domain.Content;
import cms.content.domain.ContentStatus;
import cms.content.dto.ContentDto;
import cms.content.exception.ContentNotFoundException;
import cms.content.repository.ContentRepository;
import cms.content.service.ContentService;
import cms.template.domain.Template;
import cms.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("contentService")
@RequiredArgsConstructor
@Transactional
public class ContentServiceImpl implements ContentService {

    private final ContentRepository contentRepository;

    @Override
    public Long createContent(ContentDto contentDto) {
        // TODO: Get template and creator from appropriate sources
        Template template = null; // Get from template service
        User creator = null; // Get from security context
        
        Content content = Content.createContent(
            contentDto.getTitle(),
            contentDto.getContent(),
            template,
            creator
        );
        
        if (contentDto.getExpiredAt() != null) {
            content.setExpiredAt(contentDto.getExpiredAt());
        }
        
        return contentRepository.save(content).getId();
    }

    @Override
    public void updateContent(Long contentId, ContentDto contentDto) {
        Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new ContentNotFoundException(contentId));
            
        content.setTitle(contentDto.getTitle());
        content.setContent(contentDto.getContent());
        content.setDescription(contentDto.getDescription());
        
        if (contentDto.getExpiredAt() != null) {
            content.setExpiredAt(contentDto.getExpiredAt());
        }
    }

    @Override
    public void deleteContent(Long contentId) {
        Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new ContentNotFoundException(contentId));
        content.setDeleted(true);
        contentRepository.save(content);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentDto getContent(Long contentId) {
        Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new ContentNotFoundException(contentId));
        return convertToDto(content);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContentDto> getContents(Pageable pageable) {
        return contentRepository.findAll(pageable)
            .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContentDto> searchContents(String keyword, Pageable pageable) {
        return contentRepository.searchContents(keyword, pageable)
            .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContentDto> getPublishedContents(Pageable pageable) {
        return contentRepository.findPublishedContents(pageable)
            .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContentDto> getContentsByStatus(ContentStatus status, Pageable pageable) {
        return contentRepository.findByStatus(status, pageable)
            .map(this::convertToDto);
    }

    @Override
    public void increaseViewCount(Long contentId) {
        // TODO: Implement view count increase
    }

    @Override
    public Long createVersion(Long contentId) {
        // TODO: Implement version creation
        return null;
    }

    @Override
    public void restoreVersion(Long contentId, Long versionId) {
        // TODO: Implement version restoration
    }

    private ContentDto convertToDto(Content content) {
        ContentDto dto = new ContentDto();
        dto.setId(content.getId());
        dto.setTitle(content.getTitle());
        dto.setContent(content.getContent());
        dto.setDescription(content.getDescription());
        dto.setStatus(content.getStatus());
        dto.setPublishedAt(content.getPublishedAt());
        dto.setExpiredAt(content.getExpiredAt());
        dto.setCreatedAt(content.getCreatedAt());
        dto.setUpdatedAt(content.getUpdatedAt());
        return dto;
    }
} 