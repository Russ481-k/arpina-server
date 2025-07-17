package cms.content.service.impl;

import cms.content.domain.ContentBlock;
import cms.content.domain.ContentBlockHistory;
import cms.content.dto.ContentBlockCreateRequest;
import cms.content.dto.ContentBlockReorderRequest;
import cms.content.dto.ContentBlockResponse;
import cms.content.dto.ContentBlockUpdateRequest;
import cms.content.dto.ContentBlockHistoryResponse;
import cms.content.exception.ContentBlockHistoryNotFoundException;
import cms.content.exception.ContentBlockNotFoundException;
import cms.content.repository.ContentBlockHistoryRepository;
import cms.content.repository.ContentBlockRepository;
import cms.content.service.ContentBlockService;
import cms.file.entity.CmsFile;
import cms.file.repository.FileRepository;
import cms.menu.domain.Menu;
import cms.menu.repository.MenuRepository;
import cms.common.util.IpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cms.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentBlockServiceImpl implements ContentBlockService {

    private final ContentBlockRepository contentBlockRepository;
    private final MenuRepository menuRepository;
    private final FileRepository fileRepository;
    private final ContentBlockHistoryRepository historyRepository;
    private static final int MAX_HISTORY_COUNT = 10;

    @Override
    @Transactional(readOnly = true)
    public List<ContentBlockResponse> getContentBlocksByMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));

        return menu.getContentBlocks().stream()
                .map(ContentBlockResponse::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentBlockResponse> getContentBlocksForMainPage() {
        return contentBlockRepository.findAllByMenuIsNullOrderBySortOrderAsc().stream()
                .map(ContentBlockResponse::new)
                .collect(Collectors.toList());
    }

    @Override
    public ContentBlockResponse createContentBlock(Long menuId, ContentBlockCreateRequest request) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));

        CmsFile file = findFileById(request.getFileId());
        String currentUsername = getCurrentUsername();
        String clientIp = IpUtil.getClientIp();

        ContentBlock contentBlock = ContentBlock.builder()
                .menu(menu)
                .type(request.getType())
                .content(request.getContent())
                .file(file)
                .sortOrder(request.getSortOrder())
                .createdBy(currentUsername)
                .createdIp(clientIp)
                .build();

        return new ContentBlockResponse(contentBlockRepository.save(contentBlock));
    }

    @Override
    public ContentBlockResponse createContentBlockForMainPage(ContentBlockCreateRequest request) {
        CmsFile file = findFileById(request.getFileId());
        String currentUsername = getCurrentUsername();
        String clientIp = IpUtil.getClientIp();

        ContentBlock contentBlock = ContentBlock.builder()
                .menu(null) // 메인 페이지 콘텐츠는 메뉴가 없음
                .type(request.getType())
                .content(request.getContent())
                .file(file)
                .sortOrder(request.getSortOrder())
                .createdBy(currentUsername)
                .createdIp(clientIp)
                .build();

        return new ContentBlockResponse(contentBlockRepository.save(contentBlock));
    }

    @Override
    public ContentBlockResponse updateContentBlock(Long contentId, ContentBlockUpdateRequest request) {
        ContentBlock contentBlock = findContentBlockById(contentId);
        createHistory(contentBlock); // 현재 상태를 히스토리에 저장

        CmsFile file = findFileById(request.getFileId());
        String currentUsername = getCurrentUsername();
        String clientIp = IpUtil.getClientIp();

        contentBlock.update(request.getType(), request.getContent(), file, currentUsername, clientIp);
        contentBlock.increaseVersion();

        return new ContentBlockResponse(contentBlockRepository.save(contentBlock));
    }

    @Override
    public void deleteContentBlock(Long contentId) {
        if (!contentBlockRepository.existsById(contentId)) {
            throw new EntityNotFoundException("ContentBlock not found with id: " + contentId);
        }
        contentBlockRepository.deleteById(contentId);
    }

    @Override
    public void reorderContentBlocks(ContentBlockReorderRequest request) {
        request.getReorderItems().forEach(item -> {
            ContentBlock contentBlock = contentBlockRepository.findById(item.getId())
                    .orElseThrow(() -> new EntityNotFoundException("ContentBlock not found with id: " + item.getId()));
            contentBlock.updateSortOrder(item.getSortOrder());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentBlockHistoryResponse> getHistoryByContentBlockId(Long contentId) {
        if (!contentBlockRepository.existsById(contentId)) {
            throw new EntityNotFoundException("ContentBlock not found with id: " + contentId);
        }
        return historyRepository.findByContentBlock_IdOrderByVersionDesc(contentId).stream()
                .map(ContentBlockHistoryResponse::new)
                .collect(Collectors.toList());
    }

    @Override
    public ContentBlockResponse restoreFromHistory(Long historyId) {
        ContentBlockHistory history = findHistoryById(historyId);
        ContentBlock contentBlock = history.getContentBlock();
        createHistory(contentBlock); // 복원 전 현재 상태를 히스토리에 저장

        CmsFile file = findFileById(history.getFileId());
        String currentUsername = getCurrentUsername();
        String clientIp = IpUtil.getClientIp();

        contentBlock.restore(history, file, currentUsername, clientIp);
        contentBlock.increaseVersion();

        return new ContentBlockResponse(contentBlockRepository.save(contentBlock));
    }

    private void createHistory(ContentBlock contentBlock) {
        ContentBlockHistory history = ContentBlockHistory.builder()
                .contentBlock(contentBlock)
                .version(contentBlock.getVersion())
                .type(contentBlock.getType())
                .content(contentBlock.getContent())
                .fileId(contentBlock.getFile() != null ? contentBlock.getFile().getFileId() : null)
                .createdBy(getCurrentUsername())
                .createdIp(IpUtil.getClientIp())
                .build();
        historyRepository.save(history);

        // 히스토리 개수 관리
        long historyCount = historyRepository.countByContentBlock_Id(contentBlock.getId());
        if (historyCount > MAX_HISTORY_COUNT) {
            historyRepository.findFirstByContentBlock_IdOrderByVersionAsc(contentBlock.getId())
                    .ifPresent(historyRepository::delete);
        }
    }

    // --- Helper Methods ---

    private ContentBlock findContentBlockById(Long contentId) {
        return contentBlockRepository.findById(contentId)
                .orElseThrow(() -> new ContentBlockNotFoundException(contentId));
    }

    private ContentBlockHistory findHistoryById(Long historyId) {
        return historyRepository.findById(historyId)
                .orElseThrow(() -> new ContentBlockHistoryNotFoundException(historyId));
    }

    private CmsFile findFileById(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId));
    }

    private String getCurrentUsername() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return principal.toString();
    }

}