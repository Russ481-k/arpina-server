package cms.content.service;

import cms.content.dto.ContentBlockCreateRequest;
import cms.content.dto.ContentBlockReorderRequest;
import cms.content.dto.ContentBlockResponse;
import cms.content.dto.ContentBlockUpdateRequest;
import cms.content.dto.ContentBlockHistoryResponse;

import java.util.List;

public interface ContentBlockService {

    List<ContentBlockResponse> getContentBlocksByMenu(Long menuId);

    List<ContentBlockResponse> getContentBlocksForMainPage();

    ContentBlockResponse createContentBlock(Long menuId, ContentBlockCreateRequest request);

    ContentBlockResponse createContentBlockForMainPage(ContentBlockCreateRequest request);

    ContentBlockResponse updateContentBlock(Long contentId, ContentBlockUpdateRequest request);

    void deleteContentBlock(Long contentId);

    void reorderContentBlocks(ContentBlockReorderRequest request);

    List<ContentBlockHistoryResponse> getHistoryByContentBlockId(Long contentId);

    ContentBlockResponse restoreFromHistory(Long historyId);
}