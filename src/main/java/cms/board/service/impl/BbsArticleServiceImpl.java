package cms.board.service.impl;

import cms.board.domain.BbsArticleDomain;
import cms.board.domain.BbsMasterDomain;
import cms.board.dto.BbsArticleDto;
import cms.board.repository.BbsArticleRepository;
import cms.board.repository.BbsMasterRepository;
import cms.board.service.BbsArticleService;
import cms.common.exception.BbsArticleNotFoundException;
import cms.common.exception.BbsMasterNotFoundException;
import cms.common.exception.InvalidParentArticleException;
import cms.common.exception.UnauthorizedAccessException;
import cms.common.exception.FilePolicyViolationException;
import cms.file.service.FileService;
import cms.file.entity.CmsFile;
import cms.file.dto.AttachmentInfoDto;
import cms.menu.repository.MenuRepository;
import cms.menu.domain.Menu;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class BbsArticleServiceImpl implements BbsArticleService {

    private static final String ARTICLE_ATTACHMENT_MENU_TYPE = "ARTICLE_ATTACHMENT";
    public static final String EDITOR_EMBEDDED_MEDIA = "EDITOR_EMBEDDED_MEDIA";

    private final BbsArticleRepository bbsArticleRepository;
    private final BbsMasterRepository bbsMasterRepository;
    private final MenuRepository menuRepository;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    @Value("${app.api.base-url}")
    private String appApiBaseUrl;

    @Override
    @Transactional
    public BbsArticleDto createArticle(BbsArticleDto articleDto, String editorContentJson,
            List<MultipartFile> mediaFiles, String mediaLocalIds, List<MultipartFile> attachments) {
        String[] mediaLocalIdsArray = (mediaLocalIds != null && !mediaLocalIds.isEmpty()) ? mediaLocalIds.split(",")
                : new String[0];

        log.debug("[createArticle] Received DTO content (length: {}): {}",
                articleDto.getContent() != null ? articleDto.getContent().length() : "null",
                articleDto.getContent() != null && articleDto.getContent().length() > 200
                        ? articleDto.getContent().substring(0, 200) + "..."
                        : articleDto.getContent());
        log.debug("[createArticle] Received mediaLocalIds (from array): {}", Arrays.toString(mediaLocalIdsArray));
        log.debug("[createArticle] Received mediaFiles count: {}", mediaFiles != null ? mediaFiles.size() : 0);
        log.debug("[createArticle] Received attachments count: {}", attachments != null ? attachments.size() : 0);

        BbsMasterDomain bbsMaster = bbsMasterRepository.findById(articleDto.getBbsId())
                .orElseThrow(() -> new BbsMasterNotFoundException(articleDto.getBbsId()));

        if (attachments != null && !attachments.isEmpty()) {
            validateFilePolicy(bbsMaster, attachments);
        }

        Menu menu = menuRepository.findById(articleDto.getMenuId())
                .orElseThrow(() -> new RuntimeException("Menu not found with id: " + articleDto.getMenuId()));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String writer = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()))
                ? auth.getName()
                : (articleDto.getWriter() != null ? articleDto.getWriter() : "Guest");

        BbsArticleDomain parentArticle = null;
        if (articleDto.getParentNttId() != null) {
            parentArticle = bbsArticleRepository.findById(articleDto.getParentNttId())
                    .orElseThrow(() -> new BbsArticleNotFoundException(articleDto.getParentNttId()));
            if ("QNA".equals(bbsMaster.getSkinType() != null ? bbsMaster.getSkinType().name() : null)) {
                if (!parentArticle.getBbsMaster().getBbsId().equals(bbsMaster.getBbsId())) {
                    throw new InvalidParentArticleException("답변은 같은 게시판에 작성해야 합니다.");
                }
                if (!hasAdminAuth(bbsMaster)) {
                    throw new UnauthorizedAccessException("답변 작성 권한이 없습니다.");
                }
            } else {
                if (!parentArticle.getBbsMaster().getBbsId().equals(bbsMaster.getBbsId())) {
                    throw new InvalidParentArticleException("부모 게시글은 같은 게시판에 속해있어야 합니다.");
                }
            }
        }

        boolean hasImage = checkContentForImages(articleDto.getContent());

        BbsArticleDomain article = BbsArticleDomain.builder()
                .bbsMaster(bbsMaster)
                .menu(menu)
                .parentArticle(parentArticle)
                .threadDepth(parentArticle != null ? parentArticle.getThreadDepth() + 1 : 0)
                .writer(writer)
                .title(articleDto.getTitle())
                .content(articleDto.getContent())
                .hasImageInContent(hasImage)
                .noticeState(articleDto.getNoticeState() != null ? articleDto.getNoticeState() : "N")
                .publishState(articleDto.getPublishState() != null ? articleDto.getPublishState() : "Y")
                .publishStartDt(articleDto.getPublishStartDt())
                .publishEndDt(articleDto.getPublishEndDt())
                .externalLink(articleDto.getExternalLink())
                .hits(0)
                .build();

        BbsArticleDomain savedArticle = bbsArticleRepository.save(article);

        String finalContentJson = articleDto.getContent();
        if (mediaFiles != null && !mediaFiles.isEmpty() && mediaLocalIdsArray.length > 0) {
            List<CmsFile> uploadedMediaFiles = fileService.uploadFiles(EDITOR_EMBEDDED_MEDIA, savedArticle.getNttId(),
                    mediaFiles);

            Map<String, Long> localIdToFileIdMap = new HashMap<>();
            for (int i = 0; i < mediaLocalIdsArray.length; i++) {
                if (i < uploadedMediaFiles.size()) {
                    localIdToFileIdMap.put(mediaLocalIdsArray[i], uploadedMediaFiles.get(i).getFileId());
                } else {
                    log.warn("mediaLocalId at index {} does not have a corresponding uploaded file. Skipping.", i);
                }
            }
            if (!localIdToFileIdMap.isEmpty()) {
                log.debug("[createArticle] localIdToFileIdMap created: {}", localIdToFileIdMap);
                finalContentJson = replaceLocalIdsInJson(articleDto.getContent(), localIdToFileIdMap);
            } else {
                log.debug("[createArticle] localIdToFileIdMap is empty or mediaLocalIds/mediaFiles were insufficient.");
            }
        }

        savedArticle.setContent(finalContentJson);

        BbsArticleDomain finalSavedArticle = bbsArticleRepository.save(savedArticle);

        if (attachments != null && !attachments.isEmpty()) {
            fileService.uploadFiles(ARTICLE_ATTACHMENT_MENU_TYPE, finalSavedArticle.getNttId(), attachments);
        }

        return convertToDto(finalSavedArticle);
    }

    private void validateFilePolicy(BbsMasterDomain bbsMaster, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        Integer attachmentLimit = bbsMaster.getAttachmentLimit();
        Integer attachmentSizeMB = bbsMaster.getAttachmentSize();

        if (attachmentLimit == null || attachmentSizeMB == null) {
            log.warn("BBS Master (ID: {}) attachment limit or size is not configured. Skipping file policy validation.",
                    bbsMaster.getBbsId());
            return;
        }

        if (files.size() > attachmentLimit) {
            throw new FilePolicyViolationException("첨부 파일 개수가 제한을 초과했습니다. (제한: " + attachmentLimit + "개)");
        }

        long totalSize = files.stream()
                .mapToLong(MultipartFile::getSize)
                .sum();

        long maxSizeInBytes = (long) attachmentSizeMB * 1024 * 1024;
        if (totalSize > maxSizeInBytes) {
            throw new FilePolicyViolationException("첨부 파일 총 용량이 제한을 초과했습니다. (제한: " + attachmentSizeMB + "MB)");
        }
    }

    private List<Long> uploadFilesForBbsMaster(BbsMasterDomain bbsMaster, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }
        return fileService.uploadFiles("BBS", bbsMaster.getBbsId(), files)
                .stream()
                .map(CmsFile::getFileId)
                .collect(Collectors.toList());
    }

    private boolean hasAdminAuth(BbsMasterDomain bbsMaster) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(grantedAuthority -> "ROLE_ADMIN".equals(grantedAuthority.getAuthority()));
    }

    private boolean checkContentForImages(String jsonContent) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return false;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            if (rootNode.has("root") && rootNode.get("root").has("children")) {
                return hasImageNodeRecursive(rootNode.get("root").get("children"));
            } else if (rootNode.isArray()) {
                return hasImageNodeRecursive(rootNode);
            } else if (rootNode.has("children")) {
                return hasImageNodeRecursive(rootNode.get("children"));
            }
            return false;
        } catch (IOException e) {
            log.error("Error parsing JSON for checkContentForImages: {}", e.getMessage());
            return false;
        }
    }

    private boolean hasImageNodeRecursive(JsonNode node) {
        if (node.isArray()) {
            for (JsonNode elementNode : node) {
                if (hasImageNodeRecursive(elementNode)) {
                    return true;
                }
            }
        } else if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            if (objectNode.has("type") && "image".equals(objectNode.get("type").asText())) {
                return true;
            }
            for (JsonNode child : objectNode) {
                if (child.isContainerNode()) {
                    if (hasImageNodeRecursive(child)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    @Transactional
    public BbsArticleDto updateArticle(Long nttId, BbsArticleDto articleDto, String editorContentJson,
            List<MultipartFile> mediaFiles, String mediaLocalIds, List<MultipartFile> attachments) {
        String[] mediaLocalIdsArray = (mediaLocalIds != null && !mediaLocalIds.isEmpty()) ? mediaLocalIds.split(",")
                : new String[0];

        log.debug("[updateArticle] nttId: {}, Received DTO content (length: {}): {}", nttId,
                articleDto.getContent() != null ? articleDto.getContent().length() : "null",
                articleDto.getContent() != null && articleDto.getContent().length() > 200
                        ? articleDto.getContent().substring(0, 200) + "..."
                        : articleDto.getContent());
        log.debug("[updateArticle] Received mediaLocalIds (from array): {}", Arrays.toString(mediaLocalIdsArray));
        log.debug("[updateArticle] Received mediaFiles count: {}", mediaFiles != null ? mediaFiles.size() : 0);
        log.debug("[updateArticle] Received attachments count: {}", attachments != null ? attachments.size() : 0);

        BbsArticleDomain article = bbsArticleRepository.findById(nttId)
                .orElseThrow(() -> new BbsArticleNotFoundException(nttId));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && (currentUsername == null || !article.getWriter().equals(currentUsername))) {
            throw new UnauthorizedAccessException("게시글 수정 권한이 없습니다.");
        }

        if (attachments != null && !attachments.isEmpty()) {
            validateFilePolicy(article.getBbsMaster(), attachments);
        }

        String finalContentJson = articleDto.getContent();
        Map<String, Long> newUploadedLocalIdToFileIdMap = new HashMap<>();

        if (mediaFiles != null && !mediaFiles.isEmpty() && mediaLocalIdsArray.length > 0) {
            List<CmsFile> uploadedNewMediaFiles = fileService.uploadFiles(EDITOR_EMBEDDED_MEDIA, nttId, mediaFiles);
            for (int i = 0; i < mediaLocalIdsArray.length; i++) {
                if (i < uploadedNewMediaFiles.size()) {
                    newUploadedLocalIdToFileIdMap.put(mediaLocalIdsArray[i], uploadedNewMediaFiles.get(i).getFileId());
                } else {
                    log.warn(
                            "mediaLocalId at index {} does not have a corresponding uploaded file for update. Skipping.",
                            i);
                }
            }
            if (!newUploadedLocalIdToFileIdMap.isEmpty()) {
                log.debug("[updateArticle] newUploadedLocalIdToFileIdMap created: {}", newUploadedLocalIdToFileIdMap);
                finalContentJson = replaceLocalIdsInJson(articleDto.getContent(), newUploadedLocalIdToFileIdMap);
            } else {
                log.debug(
                        "[updateArticle] newUploadedLocalIdToFileIdMap is empty or mediaLocalIds/mediaFiles were insufficient.");
            }
        }

        Set<Long> referencedFileIdsInContent = extractFileIdsFromJson(finalContentJson);
        List<CmsFile> existingDbMediaFiles = fileService.getList(EDITOR_EMBEDDED_MEDIA, nttId, null);

        for (CmsFile dbFile : existingDbMediaFiles) {
            if (!referencedFileIdsInContent.contains(dbFile.getFileId())) {
                try {
                    fileService.deleteFile(dbFile.getFileId());
                    log.info("Deleted unused embedded media file: {} from article: {}", dbFile.getFileId(), nttId);
                } catch (Exception e) {
                    log.error("Error deleting unused embedded media file: {} for article: {}. Error: {}",
                            dbFile.getFileId(), nttId, e.getMessage());
                }
            }
        }

        if (attachments != null) {
            List<CmsFile> existingAttachments = fileService.getList(ARTICLE_ATTACHMENT_MENU_TYPE, nttId, null);
            for (CmsFile existingFile : existingAttachments) {
                try {
                    fileService.deleteFile(existingFile.getFileId());
                    log.info("Deleted existing attachment file: {} for article: {}", existingFile.getFileId(), nttId);
                } catch (Exception e) {
                    log.error("Error deleting existing attachment file: {} for article: {}. Error: {}",
                            existingFile.getFileId(), nttId, e.getMessage());
                }
            }
            if (!attachments.isEmpty()) {
                fileService.uploadFiles(ARTICLE_ATTACHMENT_MENU_TYPE, nttId, attachments);
            }
        }

        article.update(
                articleDto.getWriter(),
                articleDto.getTitle(),
                finalContentJson,
                articleDto.getNoticeState() != null ? articleDto.getNoticeState() : article.getNoticeState(),
                articleDto.getNoticeStartDt(),
                articleDto.getNoticeEndDt(),
                articleDto.getPublishState() != null ? articleDto.getPublishState() : article.getPublishState(),
                articleDto.getPublishStartDt(),
                articleDto.getPublishEndDt(),
                articleDto.getExternalLink(),
                checkContentForImages(finalContentJson));

        return convertToDto(bbsArticleRepository.save(article));
    }

    @Override
    @Transactional
    public void deleteArticle(Long nttId) {
        BbsArticleDomain article = bbsArticleRepository.findById(nttId)
                .orElseThrow(() -> new BbsArticleNotFoundException(nttId));

        List<CmsFile> attachedFiles = fileService.getList(ARTICLE_ATTACHMENT_MENU_TYPE, nttId, null);
        for (CmsFile file : attachedFiles) {
            try {
                fileService.deleteFile(file.getFileId());
            } catch (Exception e) {
                log.error("Failed to delete file with ID {} for article {}: {}", file.getFileId(), nttId,
                        e.getMessage());
            }
        }

        List<BbsArticleDomain> replies = bbsArticleRepository
                .findRepliesByParentNttId(article.getBbsMaster().getBbsId(), nttId, Pageable.unpaged()).getContent();
        for (BbsArticleDomain reply : replies) {
            deleteArticle(reply.getNttId());
        }

        bbsArticleRepository.delete(article);
    }

    @Override
    @Transactional(readOnly = true)
    public BbsArticleDto getArticle(Long nttId) {
        BbsArticleDomain article = bbsArticleRepository.findById(nttId)
                .orElseThrow(() -> new BbsArticleNotFoundException(nttId));
        increaseHits(nttId);
        return convertToDto(article);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BbsArticleDto> getArticles(Long bbsId, Long menuId, Pageable pageable, boolean isAdmin) {
        Page<BbsArticleDomain> articlesPage;
        if (isAdmin) {
            articlesPage = bbsArticleRepository.findAllByBbsIdAndMenuId(bbsId, menuId, pageable);
        } else {
            articlesPage = bbsArticleRepository.findPublishedByBbsIdAndMenuId(bbsId, menuId, pageable);
        }
        return articlesPage.map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BbsArticleDto> searchArticles(Long bbsId, Long menuId, String keyword, Pageable pageable,
            boolean isAdmin) {
        Page<BbsArticleDomain> articlesPage;
        if (isAdmin) {
            articlesPage = bbsArticleRepository.searchAllByKeywordAndMenuId(bbsId, menuId, keyword, pageable);
        } else {
            articlesPage = bbsArticleRepository.searchPublishedByKeywordAndMenuId(bbsId, menuId, keyword, pageable);
        }
        return articlesPage.map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BbsArticleDto> getReplies(Long nttId, Pageable pageable) {
        BbsArticleDomain parentArticle = bbsArticleRepository.findById(nttId)
                .orElseThrow(() -> new BbsArticleNotFoundException(nttId));
        return bbsArticleRepository.findRepliesByParentNttId(parentArticle.getBbsMaster().getBbsId(), nttId, pageable)
                .map(this::convertToDto);
    }

    @Override
    @Transactional
    public void increaseHits(Long nttId) {
        BbsArticleDomain article = bbsArticleRepository.findById(nttId)
                .orElseThrow(() -> new BbsArticleNotFoundException(nttId));
        article.increaseHits();
        bbsArticleRepository.save(article);
    }

    @Override
    @Transactional
    public BbsArticleDto createBoard(BbsArticleDto boardDto) {
        BbsMasterDomain bbsMaster = bbsMasterRepository.findById(boardDto.getBbsId())
                .orElseThrow(() -> new BbsMasterNotFoundException(boardDto.getBbsId()));

        BbsArticleDomain article = BbsArticleDomain.builder()
                .bbsMaster(bbsMaster)
                .writer(boardDto.getWriter())
                .title(boardDto.getTitle())
                .content(boardDto.getContent())
                .noticeState(boardDto.getNoticeState())
                .publishState(boardDto.getPublishState())
                .publishStartDt(boardDto.getPublishStartDt())
                .publishEndDt(boardDto.getPublishEndDt())
                .externalLink(boardDto.getExternalLink())
                .hits(0)
                .build();

        BbsArticleDomain savedArticle = bbsArticleRepository.save(article);
        return convertToDto(savedArticle);
    }

    @Override
    @Transactional
    public BbsArticleDto updateBoard(Long nttId, BbsArticleDto boardDto) {
        BbsArticleDomain article = bbsArticleRepository.findById(nttId)
                .orElseThrow(() -> new BbsArticleNotFoundException(nttId));

        boolean hasImage = checkContentForImages(boardDto.getContent());

        article.update(
                article.getWriter(),
                boardDto.getTitle(),
                boardDto.getContent(),
                boardDto.getNoticeState(),
                boardDto.getNoticeStartDt(),
                boardDto.getNoticeEndDt(),
                boardDto.getPublishState(),
                boardDto.getPublishStartDt(),
                boardDto.getPublishEndDt(),
                boardDto.getExternalLink(),
                hasImage);

        return convertToDto(article);
    }

    @Override
    @Transactional
    public void deleteBoard(Long nttId) {
        BbsArticleDomain article = bbsArticleRepository.findById(nttId)
                .orElseThrow(() -> new BbsArticleNotFoundException(nttId));
        bbsArticleRepository.delete(article);
    }

    @Override
    @Transactional(readOnly = true)
    public BbsArticleDto getBoard(Long nttId) {
        BbsArticleDomain article = bbsArticleRepository.findById(nttId)
                .orElseThrow(() -> new BbsArticleNotFoundException(nttId));
        return convertToDto(article);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BbsArticleDto> getBoards(Pageable pageable) {
        return bbsMasterRepository.findAll(pageable)
                .map(this::convertToDto);
    }

    private BbsArticleDto convertToDto(BbsArticleDomain article) {
        if (article == null) {
            return null;
        }

        List<AttachmentInfoDto> attachmentInfos = Collections.emptyList();
        if (article.getNttId() != null) {
            try {
                List<CmsFile> files = fileService.getList(
                        ARTICLE_ATTACHMENT_MENU_TYPE,
                        article.getNttId(),
                        null);
                if (files != null && !files.isEmpty()) {
                    attachmentInfos = files.stream()
                            .map(cmsFile -> AttachmentInfoDto.builder()
                                    .fileId(cmsFile.getFileId())
                                    .originName(cmsFile.getOriginName())
                                    .size(cmsFile.getSize())
                                    .mimeType(cmsFile.getMimeType())
                                    .ext(cmsFile.getExt())
                                    .downloadUrl(
                                            appApiBaseUrl + "/api/v1/cms/file/public/download/" + cmsFile.getFileId())
                                    .build())
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.error("Failed to fetch attachments for article {}: {}", article.getNttId(), e.getMessage(), e);
            }
        }

        String skinTypeName = null;
        if (article.getBbsMaster() != null && article.getBbsMaster().getSkinType() != null) {
            skinTypeName = article.getBbsMaster().getSkinType().name();
        }

        Long menuDomainId = null;
        if (article.getMenu() != null && article.getMenu().getId() != null) {
            menuDomainId = article.getMenu().getId();
        }

        return BbsArticleDto.builder()
                .nttId(article.getNttId())
                .bbsId(article.getBbsMaster() != null ? article.getBbsMaster().getBbsId() : null)
                .parentNttId(article.getParentArticle() != null ? article.getParentArticle().getNttId() : null)
                .threadDepth(article.getThreadDepth())
                .writer(article.getWriter())
                .title(article.getTitle())
                .content(article.getContent())
                .hasImageInContent(article.isHasImageInContent())
                .hasAttachment(!attachmentInfos.isEmpty())
                .noticeState(article.getNoticeState())
                .noticeStartDt(article.getNoticeStartDt())
                .noticeEndDt(article.getNoticeEndDt())
                .publishState(article.getPublishState())
                .publishStartDt(article.getPublishStartDt())
                .publishEndDt(article.getPublishEndDt())
                .externalLink(article.getExternalLink())
                .hits(article.getHits())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .attachments(attachmentInfos)
                .skinType(skinTypeName)
                .menuId(menuDomainId)
                .build();
    }

    private BbsArticleDto convertToDto(BbsMasterDomain bbsMaster) {
        if (bbsMaster == null) {
            return null;
        }
        log.warn(
                "Attempting to convert BbsMasterDomain (ID: {}) to BbsArticleDto. This is potentially an error in service logic.",
                bbsMaster.getBbsId());

        String skinTypeName = null;
        if (bbsMaster.getSkinType() != null) {
            skinTypeName = bbsMaster.getSkinType().name();
        }

        return BbsArticleDto.builder()
                .bbsId(bbsMaster.getBbsId())
                .title(bbsMaster.getBbsName())
                .skinType(skinTypeName)
                .build();
    }

    private String replaceLocalIdsInJson(String editorContentJson, Map<String, Long> localIdToFileIdMap) {
        if (editorContentJson == null || localIdToFileIdMap == null || localIdToFileIdMap.isEmpty()) {
            return editorContentJson;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(editorContentJson);
            if (rootNode.has("root") && rootNode.get("root").has("children")) {
                traverseAndReplace(rootNode.get("root").get("children"), localIdToFileIdMap);
            } else if (rootNode.isArray()) {
                traverseAndReplace(rootNode, localIdToFileIdMap);
            } else if (rootNode.has("children")) {
                traverseAndReplace(rootNode.get("children"), localIdToFileIdMap);
            }

            return objectMapper.writeValueAsString(rootNode);
        } catch (IOException e) {
            log.error("Error parsing or processing editor JSON content: {}", e.getMessage());
            return editorContentJson;
        }
    }

    private void traverseAndReplace(JsonNode node, Map<String, Long> localIdToFileIdMap) {
        if (node.isArray()) {
            for (JsonNode elementNode : node) {
                traverseAndReplace(elementNode, localIdToFileIdMap);
            }
        } else if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            if (objectNode.has("type") && (objectNode.get("type").asText().equals("image")
                    || objectNode.get("type").asText().equals("video"))) {
                if (objectNode.has("src")) {
                    String srcValue = objectNode.get("src").asText();
                    log.debug("[traverseAndReplace] Found media node with src: {}", srcValue);
                    if (localIdToFileIdMap.containsKey(srcValue)) {
                        Long fileId = localIdToFileIdMap.get(srcValue);
                        String newSrc = appApiBaseUrl + "/api/v1/cms/file/public/view/" + fileId;
                        objectNode.put("src", newSrc);
                        log.debug("[traverseAndReplace] Replaced src '{}' with '{}' (File ID: {})", srcValue, newSrc,
                                fileId);
                    } else {
                        log.warn(
                                "[traverseAndReplace] No mapping found for src: '{}'. It will not be replaced. Map keys: {}",
                                srcValue, localIdToFileIdMap.keySet());
                    }
                }
            }

            objectNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isContainerNode()) {
                    traverseAndReplace(entry.getValue(), localIdToFileIdMap);
                }
            });
        }
    }

    private Set<Long> extractFileIdsFromJson(String jsonContent) {
        Set<Long> fileIds = new HashSet<>();
        if (jsonContent == null || jsonContent.isEmpty()) {
            return fileIds;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            if (rootNode.has("root") && rootNode.get("root").has("children")) {
                traverseAndExtractFileIdsRecursive(rootNode.get("root").get("children"), fileIds);
            } else if (rootNode.isArray()) {
                traverseAndExtractFileIdsRecursive(rootNode, fileIds);
            } else if (rootNode.has("children")) {
                traverseAndExtractFileIdsRecursive(rootNode.get("children"), fileIds);
            }
            log.debug("[extractFileIdsFromJson] Extracted file IDs: {}", fileIds);
            return fileIds;
        } catch (IOException e) {
            log.error("Error parsing JSON for extractFileIdsFromJson: {}", e.getMessage());
            return fileIds;
        }
    }

    private void traverseAndExtractFileIdsRecursive(JsonNode node, Set<Long> fileIds) {
        if (node.isArray()) {
            for (JsonNode elementNode : node) {
                traverseAndExtractFileIdsRecursive(elementNode, fileIds);
            }
        } else if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            if (objectNode.has("type") &&
                    ("image".equals(objectNode.get("type").asText())
                            || "video".equals(objectNode.get("type").asText()))) {
                if (objectNode.has("src")) {
                    String srcValue = objectNode.get("src").asText();
                    if (srcValue != null && !srcValue.startsWith("blob:")) {
                        Long fileId = parseFileIdFromSrc(srcValue);
                        if (fileId != null) {
                            fileIds.add(fileId);
                        } else {
                            log.warn("[traverseAndExtractFileIdsRecursive] Could not parse fileId from src: {}",
                                    srcValue);
                        }
                    }
                }
            }

            objectNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isContainerNode()) {
                    traverseAndExtractFileIdsRecursive(entry.getValue(), fileIds);
                }
            });
        }
    }

    private Long parseFileIdFromSrc(String src) {
        if (src == null)
            return null;

        // Handle "fileId:123" pattern (for backward compatibility if ever used)
        String fileIdPrefix = "fileId:";
        if (src.startsWith(fileIdPrefix)) {
            try {
                return Long.parseLong(src.substring(fileIdPrefix.length()));
            } catch (NumberFormatException e) {
                log.warn("Could not parse fileId from prefixed src: {}", src, e);
                return null;
            }
        }

        // Handle full URL pattern like "http://.../api/v1/cms/file/public/view/123"
        // A more robust way might involve java.net.URI if URLs can be complex
        String viewPathSegment = "/api/v1/cms/file/public/view/";
        int lastSegmentIndex = src.lastIndexOf(viewPathSegment);

        if (lastSegmentIndex != -1) {
            String potentialIdWithPath = src.substring(lastSegmentIndex + viewPathSegment.length());
            // Extract only the numeric part, handling potential query params or extra path
            // segments if any
            String numericId = potentialIdWithPath.split("[^0-9]")[0];
            if (!numericId.isEmpty()) {
                try {
                    return Long.parseLong(numericId);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse numeric fileId from URL segment: {}. Original src: {}", numericId, src,
                            e);
                    return null;
                }
            }
        }

        // If it's just a number string, assume it's a fileId directly (less likely for
        // src)
        // try {
        // return Long.parseLong(src);
        // } catch (NumberFormatException e) {
        // // Not a simple number
        // }

        log.debug("FileId could not be parsed from src: {}", src);
        return null;
    }
}