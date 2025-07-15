package cms.menu.dto;

import cms.menu.domain.MenuType;
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

    private Long boardId;
    private String boardName;
    private String boardSkinType;
    private String boardReadAuth;
    private String boardWriteAuth;
    private Integer boardAttachmentLimit;
    private Integer boardAttachmentSize;
}