package cms.content.dto;

import cms.content.domain.ContentBlockHistory;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ContentBlockHistoryResponse {
    private final Long id;
    private final int version;
    private final LocalDateTime createdAt;
    private final String createdBy;

    public ContentBlockHistoryResponse(ContentBlockHistory history) {
        this.id = history.getId();
        this.version = history.getVersion();
        this.createdAt = history.getCreatedAt();
        this.createdBy = history.getCreatedBy();
    }
}