package cms.file.service;

import cms.file.entity.CmsFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface FileService {
    List<CmsFile> uploadFiles(String menu, Long menuId, List<MultipartFile> files);

    List<CmsFile> getList(String menu, Long menuId, String publicYn);

    List<CmsFile> getPublicList(String menu, Long menuId);

    CmsFile getFile(Long fileId);

    CmsFile updateFile(Long fileId, CmsFile file);

    void deleteFile(Long fileId);

    void updateFileOrder(List<CmsFile> files);

    Resource loadFileAsResource(String savedName);

    List<CmsFile> getAllFiles(String menu, String publicYn, int page, int size);

    /**
     * 연결된 게시글이 없는 고아 파일들을 삭제합니다.
     * 
     * @param menuTypes 대상 메뉴 타입 목록 (예: ARTICLE_ATTACHMENT, EDITOR_EMBEDDED_MEDIA)
     * @return 삭제된 파일 개수
     */
    int deleteOrphanedFilesByMissingArticle(List<String> menuTypes);

    /**
     * 지정된 메뉴 타입들의 파일 개수를 반환합니다.
     * 
     * @param menuTypes 대상 메뉴 타입 목록
     * @return 파일 개수
     */
    long countFilesByMenuTypes(List<String> menuTypes);

    /**
     * 파일 ID 추출 로직이 올바르게 작동하는지 검증합니다.
     * 
     * @return 검증 통과 여부
     */
    boolean validateFileIdExtractionLogic();

}