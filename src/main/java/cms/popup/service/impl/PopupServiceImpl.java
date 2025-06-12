package cms.popup.service.impl;

import cms.file.entity.CmsFile;
import cms.file.service.FileService;
import cms.popup.domain.Popup;
import cms.popup.dto.AdminPopupRes;
import cms.popup.dto.PopupDataReq;
import cms.popup.dto.PopupDto;
import cms.popup.dto.PopupRes;
import cms.popup.repository.PopupRepository;
import cms.popup.service.PopupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopupServiceImpl implements PopupService {

    private final PopupRepository popupRepository;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    private static final String POPUP_FILE_CATEGORY = "POPUP_CONTENT";

    @Override
    @Transactional
    public PopupDto createPopup(PopupDataReq popupData, String contentJson, List<MultipartFile> mediaFiles,
            String[] mediaLocalIdsArray) {

        LocalDateTime startDate = popupData.getStartDate() != null ? popupData.getStartDate() : LocalDateTime.now();
        LocalDateTime endDate = popupData.getEndDate() != null ? popupData.getEndDate()
                : LocalDateTime.now().plusDays(7);

        // 1. Popup 메타데이터 저장
        Popup preSavedPopup = Popup.builder()
                .title(popupData.getTitle())
                .content("") // 내용은 파일 처리 후 업데이트
                .startDate(startDate)
                .endDate(endDate)
                .isVisible(popupData.isVisible())
                .displayOrder(popupData.getDisplayOrder())
                .build();
        Popup savedPopup = popupRepository.save(preSavedPopup);
        Long popupId = savedPopup.getId();

        // 2. 파일 업로드 및 content JSON 업데이트
        String finalContentJson = contentJson;
        List<String> mediaLocalIds = (mediaLocalIdsArray != null) ? Arrays.asList(mediaLocalIdsArray)
                : Collections.emptyList();

        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            List<CmsFile> uploadedFiles = fileService.uploadFiles(POPUP_FILE_CATEGORY, popupId, mediaFiles);

            Map<String, Long> localIdToFileIdMap = new HashMap<>();
            for (int i = 0; i < mediaLocalIds.size(); i++) {
                if (i < uploadedFiles.size()) {
                    localIdToFileIdMap.put(mediaLocalIds.get(i), uploadedFiles.get(i).getFileId());
                }
            }

            if (!localIdToFileIdMap.isEmpty()) {
                finalContentJson = replaceLocalIdsInJson(contentJson, localIdToFileIdMap);
            }
        }

        savedPopup.setContent(finalContentJson);
        Popup finalPopup = popupRepository.save(savedPopup);

        return PopupDto.from(finalPopup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminPopupRes> getPopupsForAdmin() {
        return popupRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(AdminPopupRes::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PopupDto getPopup(Long popupId) {
        Popup popup = popupRepository.findById(popupId)
                .orElseThrow(() -> new EntityNotFoundException("Popup not found with id: " + popupId));
        return PopupDto.from(popup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PopupRes> getActivePopups() {
        return popupRepository.findActivePopups(LocalDateTime.now()).stream()
                .map(PopupRes::from)
                .collect(Collectors.toList());
    }

    private String replaceLocalIdsInJson(String editorContentJson, Map<String, Long> localIdToFileIdMap) {
        if (editorContentJson == null || editorContentJson.isEmpty() || localIdToFileIdMap == null
                || localIdToFileIdMap.isEmpty()) {
            return editorContentJson;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(editorContentJson);
            traverseAndReplace(rootNode, localIdToFileIdMap);
            return objectMapper.writeValueAsString(rootNode);
        } catch (IOException e) {
            log.error("Error processing JSON for replacing local IDs: {}", e.getMessage(), e);
            // In case of an error, return the original JSON to avoid data loss.
            return editorContentJson;
        }
    }

    private void traverseAndReplace(JsonNode node, Map<String, Long> localIdToFileIdMap) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            // Check if the node is an image and has a localId
            if ("image".equals(objectNode.path("type").asText()) && objectNode.has("localId")) {
                String localId = objectNode.get("localId").asText();
                if (localIdToFileIdMap.containsKey(localId)) {
                    long fileId = localIdToFileIdMap.get(localId);
                    // Replace src with the permanent URL/path and add fileId
                    objectNode.put("src", "/api/files/" + fileId); // Or your file serving URL structure
                    objectNode.put("fileId", fileId);
                    objectNode.remove("localId"); // Remove the temporary localId
                }
            }

            // Traverse children
            objectNode.fields().forEachRemaining(entry -> traverseAndReplace(entry.getValue(), localIdToFileIdMap));

        } else if (node.isArray()) {
            // Traverse array elements
            node.forEach(element -> traverseAndReplace(element, localIdToFileIdMap));
        }
    }

    @Override
    @Transactional
    public PopupDto updatePopup(Long popupId, cms.popup.dto.PopupUpdateReq popupUpdateReq, String contentJson,
            List<MultipartFile> mediaFiles, String[] mediaLocalIdsArray) {
        Popup popup = popupRepository.findById(popupId)
                .orElseThrow(() -> new EntityNotFoundException("Popup not found with id: " + popupId));

        // 1. 기존 콘텐츠에서 파일 ID 추출
        Set<Long> oldFileIds = extractFileIdsFromJson(popup.getContent());

        // 2. 새 파일 업로드 및 새 콘텐츠 생성
        String finalContentJson = contentJson;
        List<String> mediaLocalIds = (mediaLocalIdsArray != null) ? Arrays.asList(mediaLocalIdsArray)
                : Collections.emptyList();

        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            List<CmsFile> uploadedFiles = fileService.uploadFiles(POPUP_FILE_CATEGORY, popupId, mediaFiles);
            Map<String, Long> localIdToFileIdMap = new HashMap<>();
            for (int i = 0; i < mediaLocalIds.size(); i++) {
                if (i < uploadedFiles.size()) {
                    localIdToFileIdMap.put(mediaLocalIds.get(i), uploadedFiles.get(i).getFileId());
                }
            }
            if (!localIdToFileIdMap.isEmpty()) {
                finalContentJson = replaceLocalIdsInJson(contentJson, localIdToFileIdMap);
            }
        }

        // 3. 새 콘텐츠에서 파일 ID 추출
        Set<Long> newFileIds = extractFileIdsFromJson(finalContentJson);

        // 4. 고아 파일 삭제
        oldFileIds.stream()
                .filter(fileId -> !newFileIds.contains(fileId))
                .forEach(fileService::deleteFile);

        // 5. 팝업 정보 업데이트 (Null-safe)
        if (popupUpdateReq.getTitle() != null) {
            popup.setTitle(popupUpdateReq.getTitle());
        }
        popup.setContent(finalContentJson); // 콘텐츠는 항상 업데이트 (파일 변경이 없어도 JSON 구조는 바뀔 수 있음)
        if (popupUpdateReq.getStartDate() != null) {
            popup.setStartDate(popupUpdateReq.getStartDate());
        }
        if (popupUpdateReq.getEndDate() != null) {
            popup.setEndDate(popupUpdateReq.getEndDate());
        }
        if (popupUpdateReq.getIsVisible() != null) {
            popup.updateVisibility(popupUpdateReq.getIsVisible());
        }
        if (popupUpdateReq.getDisplayOrder() != null) {
            popup.updateDisplayOrder(popupUpdateReq.getDisplayOrder());
        }

        Popup updatedPopup = popupRepository.save(popup);
        return PopupDto.from(updatedPopup);
    }

    @Override
    @Transactional
    public void deletePopup(Long popupId) {
        Popup popup = popupRepository.findById(popupId)
                .orElseThrow(() -> new EntityNotFoundException("Popup not found with id: " + popupId));

        // 1. 연결된 파일 삭제
        Set<Long> fileIds = extractFileIdsFromJson(popup.getContent());
        fileIds.forEach(fileService::deleteFile);

        // 2. 팝업 삭제
        popupRepository.delete(popup);
    }

    @Override
    @Transactional
    public void updateVisibility(Long popupId, cms.popup.dto.PopupVisibilityReq req) {
        Popup popup = popupRepository.findById(popupId)
                .orElseThrow(() -> new EntityNotFoundException("Popup not found with id: " + popupId));
        popup.updateVisibility(req.getIsVisible());
        popupRepository.save(popup);
    }

    @Override
    @Transactional
    public void updateOrder(cms.popup.dto.PopupOrderReq req) {
        List<Long> orderedIds = req.getOrderedIds();
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }

        List<Popup> popups = popupRepository.findAllById(orderedIds);
        Map<Long, Popup> popupMap = popups.stream()
                .collect(Collectors.toMap(Popup::getId, p -> p));

        for (int i = 0; i < orderedIds.size(); i++) {
            Long popupId = orderedIds.get(i);
            Popup popup = popupMap.get(popupId);
            if (popup != null) {
                popup.updateDisplayOrder(i + 1);
            }
        }

        popupRepository.saveAll(popups);
    }

    private Set<Long> extractFileIdsFromJson(String jsonContent) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            Set<Long> fileIds = new HashSet<>();
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            traverseAndExtract(rootNode, fileIds);
            return fileIds;
        } catch (IOException e) {
            log.error("Error extracting file IDs from JSON: {}", e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    private void traverseAndExtract(JsonNode node, Set<Long> fileIds) {
        if (node.isObject()) {
            if (node.has("fileId")) {
                fileIds.add(node.get("fileId").asLong());
            }
            node.fields().forEachRemaining(entry -> traverseAndExtract(entry.getValue(), fileIds));
        } else if (node.isArray()) {
            node.forEach(element -> traverseAndExtract(element, fileIds));
        }
    }
}