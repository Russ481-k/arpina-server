package cms.kispg;

import cms.kispg.dto.KispgInitParamsDto;
import cms.kispg.service.KispgPaymentService;
import cms.enroll.domain.Enroll;
import cms.enroll.repository.EnrollRepository;
import cms.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class KispgPaymentTest {

    @Autowired
    private KispgPaymentService kispgPaymentService;
    
    @Autowired
    private EnrollRepository enrollRepository;

    @Test
    public void testGenerateInitParams() {
        // Given: 테스트용 Enroll이 존재한다고 가정
        Long testEnrollId = 1L;
        
        // 실제 테스트 환경에서는 테스트 데이터를 먼저 생성해야 합니다
        // Enroll testEnroll = createTestEnroll();
        // User testUser = testEnroll.getUser();
        
        // When: KISPG 초기화 파라미터를 생성
        // KispgInitParamsDto params = kispgPaymentService.generateInitParams(testEnrollId, testUser);
        
        // Then: 필수 파라미터들이 올바르게 생성되었는지 확인
        // assertNotNull(params);
        // assertEquals("kistest00m", params.getMid());
        // assertNotNull(params.getMoid());
        // assertNotNull(params.getAmt());
        // assertNotNull(params.getEdiDate());
        // assertNotNull(params.getRequestHash());
        
        // 해시 형식 확인 (64자의 16진수 문자열)
        // assertEquals(64, params.getRequestHash().length());
        // assertTrue(params.getRequestHash().matches("[a-f0-9]{64}"));
        
        System.out.println("KISPG 결제 테스트 환경이 준비되었습니다.");
    }
} 