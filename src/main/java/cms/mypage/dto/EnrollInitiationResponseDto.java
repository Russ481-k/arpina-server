package cms.mypage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollInitiationResponseDto {
    private Long enrollId;
    private Long lessonId;
    private String paymentPageUrl; // KISPG 결제 페이지 URL
    private OffsetDateTime paymentExpiresAt; // 결제 만료 시간 (UTC, 5분 후)
} 