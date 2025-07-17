package cms.content.dto;

import cms.content.domain.ContentBlock;
import cms.content.domain.ContentBlockFile;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor
public class ContentBlockResponse {
    private Long id;
    private Long menuId;
    private String type;
    private String content;
    private int sortOrder;
    private List<FileDetailDto> files;
    private LocalDateTime createdDate;
    private String createdBy;
    private LocalDateTime updatedDate;
    private String updatedBy;

    @Getter
    @NoArgsConstructor
    public static class FileDetailDto {
        private Long fileId;
        private String fileUrl;
        private int sortOrder;

        public FileDetailDto(ContentBlockFile contentBlockFile) {
            this.fileId = contentBlockFile.getFile().getFileId();
            this.fileUrl = "/files/" + contentBlockFile.getFile().getSavedName();
            this.sortOrder = contentBlockFile.getSortOrder();
        }
    }

    public ContentBlockResponse(ContentBlock contentBlock) {
        this.id = contentBlock.getId();
        if (contentBlock.getMenu() != null) {
            this.menuId = contentBlock.getMenu().getId();
        }
        this.type = contentBlock.getType();
        this.content = contentBlock.getContent();
        this.sortOrder = contentBlock.getSortOrder();
        this.createdDate = contentBlock.getCreatedDate();
        this.createdBy = contentBlock.getCreatedBy();
        this.updatedDate = contentBlock.getUpdatedDate();
        this.updatedBy = contentBlock.getUpdatedBy();

        if (contentBlock.getFiles() != null && !contentBlock.getFiles().isEmpty()) {
            this.files = contentBlock.getFiles().stream()
                    .map(FileDetailDto::new)
                    .collect(Collectors.toList());
        }
    }
}