package buncheoleasy.payment.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "toss.payments")
public record TossPaymentsProperties(
    @NotBlank String clientKey,
    @NotBlank String secretKey,
    @NotBlank String confirmUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout) {}
