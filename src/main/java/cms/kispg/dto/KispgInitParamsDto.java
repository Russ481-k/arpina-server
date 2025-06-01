package cms.kispg.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KispgInitParamsDto {
    private String mid;         // 상점 ID
    private String moid;        // 주문번호 (enrollId 기반)
    private String amt;         // 결제 금액
    private String itemName;    // 상품명
    private String buyerName;   // 구매자명
    private String buyerTel;    // 구매자 전화번호
    private String buyerEmail;  // 구매자 이메일
    private String returnUrl;   // 결제 후 리디렉션 URL
    private String notifyUrl;   // 결제 결과 통지 URL (Webhook)
    private String ediDate;     // 전문 생성일시 (yyyyMMddHHmmss)
    private String requestHash; // 요청 해시값 (보안 검증용)

    // Fields added based on user request for detailed payment information
    private String goodsSplAmt; // 공급가액 (VAT 제외 금액)
    private String goodsVat;    // 부가세액
    private String userIp;      // 사용자 IP 주소 (선택 사항)
    private String mbsUsrId;    // 가맹점 고객 ID (선택 사항)
    private String mbsReserved1; // 가맹점 예약필드1 (예: enrollId)
} 