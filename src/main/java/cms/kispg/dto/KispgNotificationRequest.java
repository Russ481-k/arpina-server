package cms.kispg.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class KispgNotificationRequest {
    // Fields based on KISPG documentation (Docs/cms/kispg-payment-integration.md)
    private String mid;         // 상점 ID
    private String tid;         // KISPG 거래 ID
    private String moid;        // 주문번호 (CMS의 enrollId 또는 관련 고유값)
    private String amt;         // 결제 금액 (문자열로 수신될 수 있음)
    private String resultCode;  // 결과 코드 (예: "0000" 또는 "3001" 등 KISPG 정의 코드)
    private String resultMsg;   // 결과 메시지
    private String payMethod;   // 결제 수단 (예: CARD, VBANK)
    private String approveNo;   // 승인 번호 (카드 결제 시)
    private String cardQuota;   // 할부 개월 (카드 결제 시)
    private String encData;     // KISPG 암호화 데이터 (해시 검증용)
    private String buyerName;   // 구매자명 (로깅/참조용)
    private String buyerTel;    // 구매자 전화번호 (로깅/참조용)
    private String buyerEmail;  // 구매자 이메일 (로깅/참조용)
    private String vactBankName; // 가상계좌 은행명 (가상계좌 결제 시)
    private String vactNum;      // 가상계좌 번호 (가상계좌 결제 시)
    private String vactName;     // 가상계좌 예금주명 (가상계좌 결제 시)
    private String vactDate;     // 가상계좌 입금 만료일 (YYYYMMDD)
    private String vactTime;     // 가상계좌 입금 만료시간 (HHMMSS)
    
    // 추가적으로 KISPG에서 제공할 수 있는 기타 필드들
} 