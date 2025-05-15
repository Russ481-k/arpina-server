package cms.enterprise.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Size;
import javax.validation.constraints.Min;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "입주 기업 정보 수정 요청 정보")
public class UpdateEnterpriseRequest {

    @Min(value = 1900, message = "유효한 연도를 입력해주세요.")
    @Schema(description = "입주 연도", example = "2023")
    private Integer year;

    @Size(min = 1, max = 100, message = "기업명은 1자 이상 100자 이하로 입력해주세요.")
    @Schema(description = "기업명", example = "수정된 (주)샘플기업")
    private String name;

    @Size(max = 1000, message = "기업 설명은 1000자 이하로 입력해주세요.")
    @Schema(description = "기업 간략 설명", example = "더욱 새로워진 기술을 선보입니다.")
    private String description;

    @Schema(description = "기업 로고 또는 대표 이미지 URL/경로", example = "/uploads/images/updated_logo.png")
    private String image;

    @Size(max = 50, message = "대표자명은 50자 이하로 입력해주세요.")
    @Schema(description = "대표자명", example = "이순신")
    private String representative;

    @Schema(description = "설립일 (YYYY-MM-DD)", example = "2021-05-20")
    private LocalDate established;

    @Size(max = 100, message = "업종은 100자 이하로 입력해주세요.")
    @Schema(description = "업종", example = "AI, 로보틱스")
    private String businessType;

    @Schema(description = "기업 상세 소개 (HTML 또는 일반 텍스트)", example = "<p>핵심 기술이 업데이트 되었습니다.</p>")
    private String detail;

    @Schema(description = "'펼쳐보기' 버튼 표시 여부", example = "false")
    private Boolean showButton;
} 