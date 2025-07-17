package cms.content.dto;

import cms.content.domain.ContentBlock;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ContentBlockResponse {
    private Long id;
    private Long menuId;
    private String type;
    private String content;
    private Long fileId;
    private String fileUrl;
    private int sortOrder;
    private LocalDateTime createdDate;
    private String createdBy;
    private LocalDateTime updatedDate;
    private String updatedBy;

    public ContentBlockResponse(ContentBlock contentBlock) {
        this.id = contentBlock.getId();
        if (contentBlock.getMenu() != null) {
            this.menuId = contentBlock.getMenu().getId();
        }
        this.type = contentBlock.getType();
        this.content = contentBlock.getContent();
        if (contentBlock.getFile() != null) {
            this.fileId = contentBlock.getFile().getFileId();
            this.fileUrl = "/files/" + contentBlock.getFile().getSavedName(); // Or a full URL
        }
        this.sortOrder = contentBlock.getSortOrder();
        this.createdDate = contentBlock.getCreatedDate();
        this.createdBy = contentBlock.getCreatedBy();
        this.updatedDate = contentBlock.getUpdatedDate();
        this.updatedBy = contentBlock.getUpdatedBy();
    }
}