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
    public PaymentDataResponse getPaymentDataByPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        Specification<Payment> spec = PaymentSpecification.paidAtBetween(startDate, endDate);
        List<Payment> payments = paymentRepository.findAll(spec);

        List<PaymentDetailDto> paymentDetailDtos = payments.stream()
                .map(PaymentDetailDto::from)
                .collect(Collectors.toList());

        return new PaymentDataResponse(paymentDetailDtos);
    }
}