package cms.external.service;

import cms.external.dto.PaymentDataResponse;
import java.time.LocalDate;

public interface ExternalApiService {
    PaymentDataResponse getPaymentDataByPeriod(LocalDate startDate, LocalDate endDate);
}