package cms.enterprise.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.constraints.Min;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "신규 입주 기업 등록 요청 정보")
public class CreateEnterpriseRequest {

    @NotNull(message = "입주 연도는 필수입니다.")
    @Min(value = 1900, message = "유효한 연도를 입력해주세요.") // 예시: 최소 연도 제한
    @Schema(description = "입주 연도", example = "2025", required = true)
    private Integer year;

    @NotBlank(message = "기업명은 필수입니다.")
    @Size(min = 1, max = 100, message = "기업명은 1자 이상 100자 이하로 입력해주세요.")
    @Schema(description = "기업명", example = "새로운 혁신 기업", required = true)
    private String name;

    @NotBlank(message = "기업 설명은 필수입니다.")
    @Size(max = 1000, message = "기업 설명은 1000자 이하로 입력해주세요.") // 예시: 길이 제한
    @Schema(description = "기업 간략 설명", example = "미래를 만들어갈 기업입니다.", required = true)
    private String description;

    @Schema(description = "기업 로고 또는 대표 이미지 URL/경로", example = "/uploads/images/new_innovation_logo.png")
    private String image;

    @Size(max = 50, message = "대표자명은 50자 이하로 입력해주세요.")
    @Schema(description = "대표자명", example = "김철수")
    private String representative;

    @Schema(description = "설립일 (YYYY-MM-DD)", example = "2025-03-01")
    private LocalDate established;

    @Size(max = 100, message = "업종은 100자 이하로 입력해주세요.")
    @Schema(description = "업종", example = "핀테크, 빅데이터")
    private String businessType;

    @Schema(description = "기업 상세 소개 (HTML 또는 일반 텍스트)", example = "<h3>주요 사업 내용</h3><ul><li>데이터 분석 플랫폼</li></ul>")
    private String detail;

    @Schema(description = "'펼쳐보기' 버튼 표시 여부 (기본값: true)", example = "false")
    private Boolean showButton; // 엔티티에서 디폴트 처리, 요청 시 명시적 값 사용
} 