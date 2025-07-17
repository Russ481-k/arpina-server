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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import cms.content.domain.ContentBlockFile;
import cms.content.repository.ContentBlockFileRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentBlockServiceImpl implements ContentBlockService {

    private final ContentBlockRepository contentBlockRepository;
    private final MenuRepository menuRepository;
    private final FileRepository fileRepository;
    private final ContentBlockHistoryRepository historyRepository;
    private final ContentBlockFileRepository contentBlockFileRepository;
    private final ObjectMapper objectMapper;
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
        String currentUsername = getCurrentUsername();
        String clientIp = IpUtil.getClientIp();

        ContentBlock contentBlock = ContentBlock.builder()
                .menu(menu)
                .type(request.getType())
                .content(request.getContent())
                .sortOrder(request.getSortOrder())
                .createdBy(currentUsername)
                .createdIp(clientIp)
                .build();

        ContentBlock savedContentBlock = contentBlockRepository.save(contentBlock);

        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            associateFilesToContentBlock(savedContentBlock, request.getFileIds());
        }

        return new ContentBlockResponse(findContentBlockById(savedContentBlock.getId()));
    }

    @Override
    public ContentBlockResponse createContentBlockForMainPage(ContentBlockCreateRequest request) {
        String currentUsername = getCurrentUsername();
        String clientIp = IpUtil.getClientIp();

        ContentBlock contentBlock = ContentBlock.builder()
                .menu(null) // 메인 페이지 콘텐츠는 메뉴가 없음
                .type(request.getType())
                .content(request.getContent())
                .sortOrder(request.getSortOrder())
                .createdBy(currentUsername)
                .createdIp(clientIp)
                .build();

        ContentBlock savedContentBlock = contentBlockRepository.save(contentBlock);

        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            associateFilesToContentBlock(savedContentBlock, request.getFileIds());
        }

        return new ContentBlockResponse(findContentBlockById(savedContentBlock.getId()));
    }

    @Override
    public ContentBlockResponse updateContentBlock(Long contentId, ContentBlockUpdateRequest request) {
        ContentBlock contentBlock = findContentBlockById(contentId);
        createHistory(contentBlock);

        String currentUsername = getCurrentUsername();
        String clientIp = IpUtil.getClientIp();

        contentBlock.update(request.getType(), request.getContent(), currentUsername, clientIp);

        // orphanRemoval=true 옵션에 따라 아래 로직이 기존 관계를 자동으로 삭제하고 새로 설정함
        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            associateFilesToContentBlock(contentBlock, request.getFileIds());
        } else {
            contentBlock.getFiles().clear(); // 모든 파일 관계를 제거
        }

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
        createHistory(contentBlock);

        String currentUsername = getCurrentUsername();
        String clientIp = IpUtil.getClientIp();

        contentBlock.restore(history, currentUsername, clientIp);

        List<Long> fileIds = parseFileIdsFromJson(history.getFileIdsJson());
        if (fileIds != null && !fileIds.isEmpty()) {
            associateFilesToContentBlock(contentBlock, fileIds);
        } else {
            contentBlock.getFiles().clear();
        }

        contentBlock.increaseVersion();

        return new ContentBlockResponse(contentBlockRepository.save(contentBlock));
    }

    private void createHistory(ContentBlock contentBlock) {
        String fileIdsJson = serializeFileIdsToJson(contentBlock.getFiles());

        ContentBlockHistory history = ContentBlockHistory.builder()
                .contentBlock(contentBlock)
                .version(contentBlock.getVersion())
                .type(contentBlock.getType())
                .content(contentBlock.getContent())
                .fileIdsJson(fileIdsJson)
                .createdBy(getCurrentUsername())
                .createdIp(IpUtil.getClientIp())
                .build();
        historyRepository.save(history);

        long historyCount = historyRepository.countByContentBlock_Id(contentBlock.getId());
        if (historyCount > MAX_HISTORY_COUNT) {
            historyRepository.findFirstByContentBlock_IdOrderByVersionAsc(contentBlock.getId())
                    .ifPresent(historyRepository::delete);
        }
    }

    private void associateFilesToContentBlock(ContentBlock contentBlock, List<Long> fileIds) {
        String currentUsername = getCurrentUsername();
        String clientIp = IpUtil.getClientIp();

        List<ContentBlockFile> newFiles = new ArrayList<>();
        for (int i = 0; i < fileIds.size(); i++) {
            Long fileId = fileIds.get(i);
            CmsFile file = findFileById(fileId);
            newFiles.add(ContentBlockFile.builder()
                    .contentBlock(contentBlock)
                    .file(file)
                    .sortOrder(i)
                    .createdBy(currentUsername)
                    .createdIp(clientIp)
                    .build());
        }

        // Jpa a CascadeType.ALL 및 orphanRemoval=true 옵션을 활용하기 위해
        // 리포지토리를 직접 호출하는 대신, 부모 엔티티의 컬렉션을 수정합니다.
        contentBlock.getFiles().clear();
        contentBlock.getFiles().addAll(newFiles);
    }

    private String serializeFileIdsToJson(List<ContentBlockFile> files) {
        if (files == null || files.isEmpty()) {
            return "[]";
        }
        List<Long> fileIds = files.stream()
                .map(cbf -> cbf.getFile().getFileId())
                .collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(fileIds);
        } catch (JsonProcessingException e) {
            // 로깅 및 예외 처리
            return "[]";
        }
    }

    private List<Long> parseFileIdsFromJson(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (JsonProcessingException e) {
            // 로깅 및 예외 처리
            return Collections.emptyList();
        }
    }

    // --- Helper Methods ---

    private ContentBlock findContentBlockById(Long contentId) {
        return contentBlockRepository.findByIdWithFiles(contentId)
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