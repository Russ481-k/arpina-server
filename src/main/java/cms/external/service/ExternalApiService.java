package cms.external.service;

import cms.external.dto.PaymentDataResponse;
import java.time.LocalDateTime;

public interface ExternalApiService {
    PaymentDataResponse getPaymentDataByPeriod(LocalDateTime startDate, LocalDateTime endDate);
}