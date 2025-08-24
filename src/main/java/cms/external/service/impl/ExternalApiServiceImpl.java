package cms.external.service.impl;

import cms.external.dto.PaymentDataResponse;
import cms.external.dto.PaymentDetailDto;
import cms.external.service.ExternalApiService;
import cms.payment.domain.Payment;
import cms.payment.repository.PaymentRepository;
import cms.payment.repository.specification.PaymentSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalApiServiceImpl implements ExternalApiService {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public PaymentDataResponse getPaymentDataByPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        // 기간 내의 결제 데이터 조회
        Specification<Payment> spec = PaymentSpecification.paidAtBetween(startDate, endDate);
        List<Payment> payments = paymentRepository.findAll(spec);

        // DTO 변환 시 현재 상태 유지하고, 미조회 데이터만 업데이트
        List<PaymentDetailDto> paymentDetailDtos = payments.stream()
                .map(payment -> {
                    PaymentDetailDto dto = PaymentDetailDto.from(payment);
                    
                    // 현재 상태를 DTO에 저장
                    Integer currentStatus = payment.getExportStatus();
                    int safeStatus = currentStatus != null ? currentStatus : 0;
                    dto.setExportStatus(safeStatus);

                    // 미조회 데이터(0)인 경우에만 조회됨(1)으로 업데이트
                    if (safeStatus == 0) {
                        payment.setExportStatus(1);
                    }
                    
                    return dto;
                })
                .collect(Collectors.toList());

        // 변경된 데이터 저장
        paymentRepository.saveAll(payments);

        return new PaymentDataResponse(paymentDetailDtos);
    }
}