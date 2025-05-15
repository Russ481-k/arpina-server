package cms.menu.dto;

import cms.menu.domain.MenuType;
// import cms.board.domain.BbsSkinType; // If BbsSkinType enum exists and is to be used directly
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PageDetailsDto {
    private Long menuId;
    private String menuName;
    private MenuType menuType;

    // Board-specific details
    private Long boardId;
    private String boardName;
    private String boardSkinType; // Or BbsSkinType if the enum is preferred and exists
    private String boardReadAuth;
    private String boardWriteAuth;
    private Integer boardAttachmentLimit;
    private Integer boardAttachmentSize;

    // Placeholder for CONTENT type (future)
    // private Long contentId;
    // private String contentLayout;

    // Placeholder for PROGRAM type (future)
    // private String programPath;
    // private String programDescription;
} 