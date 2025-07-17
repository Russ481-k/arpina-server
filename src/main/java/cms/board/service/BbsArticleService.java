package cms.board.service;

import cms.board.dto.BbsArticleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BbsArticleService {
        // Board related methods
        BbsArticleDto createBoard(BbsArticleDto boardDto);

        BbsArticleDto updateBoard(Long bbsId, BbsArticleDto boardDto);

        void deleteBoard(Long bbsId);

        BbsArticleDto getBoard(Long bbsId);

        Page<BbsArticleDto> getBoards(Pageable pageable);

        // Article related methods
        BbsArticleDto createArticle(BbsArticleDto articleDto, String editorContentJson, List<MultipartFile> mediaFiles,
                        String mediaLocalIds, List<MultipartFile> attachments);

        BbsArticleDto updateArticle(Long nttId, BbsArticleDto articleDto, String editorContentJson,
                        List<MultipartFile> mediaFiles, String mediaLocalIds, List<MultipartFile> attachments);

        void deleteArticle(Long nttId);

        BbsArticleDto getArticle(Long nttId);

        Page<BbsArticleDto> getArticles(Long bbsId, Long menuId, Pageable pageable, boolean isAdmin);

        Page<BbsArticleDto> getArticles(Long bbsId, Long menuId, Long categoryId, Pageable pageable, boolean isAdmin);

        Page<BbsArticleDto> searchArticles(Long bbsId, Long menuId, String keyword, Pageable pageable, boolean isAdmin);

        Page<BbsArticleDto> getReplies(Long nttId, Pageable pageable);

        void increaseHits(Long nttId);
}