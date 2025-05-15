package cms.enterprise.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "입주 기업 정보")
public class EnterpriseDto {

    @Schema(description = "기업 ID (Long)", example = "1")
    private Long id;

    @Schema(description = "입주 연도", example = "2024", required = true)
    private Integer year;

    @Schema(description = "기업명", example = "주식회사 샘플기업", required = true)
    private String name;

    @Schema(description = "기업 간략 설명", example = "혁신적인 기술을 선도하는 기업입니다.", required = true)
    private String description;

    @Schema(description = "기업 로고 또는 대표 이미지 URL/경로", example = "/uploads/images/sample_company_logo.png")
    private String image;

    @Schema(description = "대표자명", example = "홍길동")
    private String representative;

    @Schema(description = "설립일 (YYYY-MM-DD)", example = "2020-01-15")
    private LocalDate established;

    @Schema(description = "업종", example = "IT, 인공지능")
    private String businessType;

    @Schema(description = "기업 상세 소개 (HTML 또는 일반 텍스트)", example = "<p>저희는 AI 기반 솔루션을 개발하여...</p>")
    private String detail;

    @Schema(description = "'펼쳐보기' 버튼 표시 여부", example = "true")
    private Boolean showButton;

    @Schema(description = "생성일시 (ISO 8601)", example = "2024-01-10T10:00:00Z")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시 (ISO 8601)", example = "2024-01-10T10:00:00Z")
    private LocalDateTime updatedAt;

    @Schema(description = "생성자 ID")
    private String createdBy;
    
    @Schema(description = "수정자 ID")
    private String updatedBy;
} 