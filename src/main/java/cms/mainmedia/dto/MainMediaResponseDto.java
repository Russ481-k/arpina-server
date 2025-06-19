package cms.mainmedia.dto;

import cms.mainmedia.domain.MainMedia;
import cms.mainmedia.domain.MediaType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MainMediaResponseDto {

    private Long id;
    private String title;
    private String description;
    private MediaType mediaType;
    private int displayOrder;
    private String fileUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MainMediaResponseDto from(MainMedia mainMedia, String fileBaseUrl) {
        String fullUrl = fileBaseUrl + "/" + mainMedia.getCmsFile().getSavedName();
        return MainMediaResponseDto.builder()
                .id(mainMedia.getId())
                .title(mainMedia.getTitle())
                .description(mainMedia.getDescription())
                .mediaType(mainMedia.getMediaType())
                .displayOrder(mainMedia.getDisplayOrder())
                .fileUrl(fullUrl)
                .createdAt(mainMedia.getCreatedAt())
                .updatedAt(mainMedia.getUpdatedAt())
                .build();
    }
}