package cms.content.dto;

import cms.content.domain.ContentBlockHistory;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ContentBlockHistoryResponse {
    private final Long id;
    private final int version;
    private final String type;
    private final String content;
    private final Long fileId;
    private final LocalDateTime createdDate;
    private final String createdBy;
    private final String createdIp;
    private final LocalDateTime updatedDate;
    private final String updatedBy;
    private final String updatedIp;

    public ContentBlockHistoryResponse(ContentBlockHistory history) {
        this.id = history.getId();
        this.version = history.getVersion();
        this.type = history.getType();
        this.content = history.getContent();
        this.fileId = history.getFileId();
        this.createdDate = history.getCreatedDate();
        this.createdBy = history.getCreatedBy();
        this.createdIp = history.getCreatedIp();
        this.updatedDate = history.getUpdatedDate();
        this.updatedBy = history.getUpdatedBy();
        this.updatedIp = history.getUpdatedIp();
    }
}