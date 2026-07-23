package buncheoleasy.auth.infrastructure.oauth;

import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 REST API 클라이언트. OIDC ID 토큰에는 이름·전화번호 클레임이 없으므로 로그인 성공 직후 사용자 access token 으로 보강 조회한다.
 *
 * <ul>
 *   <li>{@code /v2/user/me}: kakao_account 의 name·phone_number (검수 승인된 동의항목만 내려옴)
 *   <li>{@code /v1/user/service_terms}: 간편가입 동의창에서 받은 약관 동의 내역
 * </ul>
 */
@Component
public class KakaoApiClient {

  private final RestClient restClient;

  public KakaoApiClient(
      @Value("${app.kakao.api-base-url:https://kapi.kakao.com}") final String apiBaseUrl) {
    this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
  }

  /** 이름·전화번호 보강 조회. 전화번호는 서비스 표준 형식(01x…)으로 정규화해 반환한다. */
  public KakaoUserInfo getUserInfo(final String accessToken) {
    KakaoUserMeResponse response =
        restClient
            .get()
            .uri("/v2/user/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(KakaoUserMeResponse.class);

    if (response == null || response.kakaoAccount() == null) {
      return new KakaoUserInfo(null, null);
    }
    return new KakaoUserInfo(
        response.kakaoAccount().name(),
        KakaoPhoneNumberNormalizer.normalize(response.kakaoAccount().phoneNumber()));
  }

  /** 간편가입 약관 동의 내역 조회. */
  public List<ServiceTermAgreement> getServiceTerms(final String accessToken) {
    KakaoServiceTermsResponse response =
        restClient
            .get()
            .uri("/v1/user/service_terms")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(KakaoServiceTermsResponse.class);

    if (response == null || response.serviceTerms() == null) {
      return List.of();
    }
    return response.serviceTerms().stream()
        .filter(term -> term.tag() != null)
        .map(
            term ->
                new ServiceTermAgreement(
                    term.tag(),
                    Boolean.TRUE.equals(term.agreed()),
                    term.agreedAt() != null ? term.agreedAt().toInstant() : null))
        .toList();
  }

  public record KakaoUserInfo(String name, String phoneNumber) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record KakaoUserMeResponse(@JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAccount(String name, @JsonProperty("phone_number") String phoneNumber) {}
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record KakaoServiceTermsResponse(@JsonProperty("service_terms") List<ServiceTerm> serviceTerms) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ServiceTerm(
        String tag, Boolean agreed, @JsonProperty("agreed_at") OffsetDateTime agreedAt) {}
  }
}
