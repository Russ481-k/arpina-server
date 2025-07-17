package cms.content.dto;

import cms.content.domain.ContentBlockHistory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ContentBlockHistoryResponse {

    private Long id;
    private int version;
    private String type;
    private String content;
    private List<Long> fileIds;
    private LocalDateTime createdDate;
    private String createdBy;

    public ContentBlockHistoryResponse(ContentBlockHistory history) {
        this.id = history.getId();
        this.version = history.getVersion();
        this.type = history.getType();
        this.content = history.getContent();
        this.createdDate = history.getCreatedDate();
        this.createdBy = history.getCreatedBy();
        this.fileIds = parseFileIdsFromJson(history.getFileIdsJson());
    }

    private List<Long> parseFileIdsFromJson(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            // ObjectMapper를 정적 유틸리티나 주입을 통해 재사용하는 것이 좋습니다.
            // 여기서는 간단하게 new로 생성합니다.
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (JsonProcessingException e) {
            // 실제 프로덕션 코드에서는 로깅을 추가해야 합니다.
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}