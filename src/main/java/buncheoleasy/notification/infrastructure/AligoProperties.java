package buncheoleasy.notification.infrastructure;

import buncheoleasy.notification.domain.AlimtalkTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aligo.alimtalk")
public record AligoProperties(
    @NotBlank String baseUrl,
    @NotBlank String apiKey,
    @NotBlank String userId,
    @NotBlank String senderKey,
    @NotBlank String sender,
    boolean failover,
    boolean testMode,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    Map<AlimtalkTemplate, String> templateCodes) {}
