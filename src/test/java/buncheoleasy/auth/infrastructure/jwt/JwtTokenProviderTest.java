package buncheoleasy.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.LoginClient;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider 단위 테스트")
class JwtTokenProviderTest {

  private static final String ACCESS_SECRET =
      "test-access-secret-test-access-secret-test-access-secret";
  private static final String REFRESH_SECRET =
      "test-refresh-secret-test-refresh-secret-test-refresh-secret";

  @Mock private RefreshTokenStore refreshTokenStore;

  private JwtTokenProvider createProvider(long accessExp, long refreshExp) {
    return createProvider(accessExp, refreshExp, 43200);
  }

  private JwtTokenProvider createProvider(long accessExp, long refreshExp, long adminExp) {
    return new JwtTokenProvider(
        ACCESS_SECRET, REFRESH_SECRET, accessExp, refreshExp, adminExp, 900, refreshTokenStore);
  }

  @Nested
  @DisplayName("issueTokens/parse 테스트")
  class IssueAndParseTest {

    @Test
    void 토큰을_발급하고_userId를_다시_파싱할_수_있다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);
      Long userId = 1L;

      // when
      TokenPair tokenPair = provider.issueTokens(userId);

      // then
      assertThat(provider.parseUserIdFromAccessToken(tokenPair.accessToken())).isEqualTo(userId);
      assertThat(provider.parseUserIdFromRefreshToken(tokenPair.refreshToken())).isEqualTo(userId);
      then(refreshTokenStore).should().save(userId, LoginClient.WEB, tokenPair.refreshToken(), 604800);
    }

    @Test
    void 관리자_토큰을_발급하면_role_claim을_파싱할_수_있다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);

      // when
      String adminToken = provider.createAdminAccessToken(1L);

      // then
      AccessTokenClaims claims = provider.parseAccessTokenClaims(adminToken);
      assertThat(claims.userId()).isEqualTo(1L);
      assertThat(claims.role()).isEqualTo("ADMIN");
    }

    @Test
    void 유저_토큰은_role_claim이_null이다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);

      // when
      TokenPair tokenPair = provider.issueTokens(1L);

      // then
      AccessTokenClaims claims = provider.parseAccessTokenClaims(tokenPair.accessToken());
      assertThat(claims.userId()).isEqualTo(1L);
      assertThat(claims.role()).isNull();
    }

    @Test
    void impersonation_토큰은_role은_null이고_imp_claim이_설정된다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);

      // when
      String impersonationToken = provider.createImpersonationAccessToken(21L);

      // then — 유저 API 를 그대로 호출해야 하므로 role 은 null(ROLE_USER), 대신 imp 로 재현 토큰임을 표시한다.
      AccessTokenClaims claims = provider.parseAccessTokenClaims(impersonationToken);
      assertThat(claims.userId()).isEqualTo(21L);
      assertThat(claims.role()).isNull();
      assertThat(claims.impersonated()).isTrue();
    }

    @Test
    void 일반_유저_토큰은_imp_claim이_없다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);

      // when
      TokenPair tokenPair = provider.issueTokens(1L);

      // then
      assertThat(provider.parseAccessTokenClaims(tokenPair.accessToken()).impersonated()).isFalse();
    }

    @Test
    void 만료된_관리자_토큰이면_예외가_발생한다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800, -1);
      String expiredAdminToken = provider.createAdminAccessToken(1L);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessTokenClaims(expiredAdminToken))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);
    }

    @Test
    void 잘못된_서명의_액세스_토큰이면_예외가_발생한다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);
      SecretKey anotherKey =
          Keys.hmacShaKeyFor(
              "another-secret-key-for-signature-123".getBytes(StandardCharsets.UTF_8));
      String invalidSignedToken = Jwts.builder().subject("1").signWith(anotherKey).compact();

      // when & then
      assertThatThrownBy(() -> provider.parseUserIdFromAccessToken(invalidSignedToken))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void 잘못된_서명의_리프레시_토큰이면_예외가_발생한다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);
      SecretKey anotherKey =
          Keys.hmacShaKeyFor(
              "another-refresh-key-for-signature-123".getBytes(StandardCharsets.UTF_8));
      String invalidSignedToken = Jwts.builder().subject("1").signWith(anotherKey).compact();

      // when & then
      assertThatThrownBy(() -> provider.parseUserIdFromRefreshToken(invalidSignedToken))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void 만료된_액세스_토큰이면_예외가_발생한다() {
      // given
      JwtTokenProvider provider = createProvider(-1, 604800);
      String expiredToken = provider.createAccessToken(1L);

      // when & then
      assertThatThrownBy(() -> provider.parseUserIdFromAccessToken(expiredToken))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);
    }

    @Test
    void 만료된_리프레시_토큰이면_예외가_발생한다() {
      // given
      JwtTokenProvider provider = createProvider(3600, -1);
      String expiredToken = provider.createRefreshToken(1L);

      // when & then
      assertThatThrownBy(() -> provider.parseUserIdFromRefreshToken(expiredToken))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);
    }

    @Test
    void subject가_숫자가_아니면_예외가_발생한다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);
      SecretKey refreshKey = Keys.hmacShaKeyFor(REFRESH_SECRET.getBytes(StandardCharsets.UTF_8));
      String token = Jwts.builder().subject("not-number").signWith(refreshKey).compact();

      // when & then
      assertThatThrownBy(() -> provider.parseUserIdFromRefreshToken(token))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }
  }

  @Nested
  @DisplayName("reissueTokens 테스트")
  class ReissueTokensTest {

    @Test
    void 기존_토큰을_검증하고_삭제한_뒤_새_토큰을_발급한다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);
      Long userId = 1L;
      String oldRefreshToken = "old-refresh-token";

      // when
      TokenPair tokenPair = provider.reissueTokens(userId, oldRefreshToken);

      // then
      assertThat(provider.parseUserIdFromAccessToken(tokenPair.accessToken())).isEqualTo(userId);
      assertThat(provider.parseUserIdFromRefreshToken(tokenPair.refreshToken())).isEqualTo(userId);

      InOrder inOrder = Mockito.inOrder(refreshTokenStore);
      inOrder.verify(refreshTokenStore).verify(userId, LoginClient.WEB, oldRefreshToken);
      inOrder.verify(refreshTokenStore).delete(userId, LoginClient.WEB);
      inOrder.verify(refreshTokenStore)
          .save(userId, LoginClient.WEB, tokenPair.refreshToken(), 604800);
    }
  }

  @Nested
  @DisplayName("앱 칸 refresh 토큰")
  class AppClientTest {

    @Test
    void 앱_토큰은_앱_칸에_저장되고_cli_app_claim_이_붙는다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);

      // when
      TokenPair tokenPair = provider.issueTokens(7L, LoginClient.APP);

      // then
      then(refreshTokenStore).should().save(7L, LoginClient.APP, tokenPair.refreshToken(), 604800);
      assertThat(refreshClaims(tokenPair.refreshToken()).get("cli")).isEqualTo("app");
      assertThat(provider.parseUserIdFromRefreshToken(tokenPair.refreshToken(), LoginClient.APP))
          .isEqualTo(7L);
    }

    @Test
    void 웹_토큰에는_cli_claim_이_없다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);

      // when
      TokenPair tokenPair = provider.issueTokens(7L);

      // then
      assertThat(refreshClaims(tokenPair.refreshToken())).doesNotContainKey("cli");
    }

    @Test
    void 웹_갱신은_앱_토큰을_거절한다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);
      String appRefresh = provider.issueTokens(7L, LoginClient.APP).refreshToken();

      // when & then
      assertThatThrownBy(() -> provider.parseUserIdFromRefreshToken(appRefresh))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void 앱_갱신은_웹_토큰을_거절한다() {
      // given — 앱 칸이 생기기 전에 발급된 웹 토큰(claim 없음)도 같은 모양이다.
      JwtTokenProvider provider = createProvider(3600, 604800);
      String webRefresh = provider.issueTokens(7L).refreshToken();

      // when & then
      assertThatThrownBy(() -> provider.parseUserIdFromRefreshToken(webRefresh, LoginClient.APP))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void 모르는_cli_값은_서명이_맞아도_거절한다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);
      SecretKey refreshKey = Keys.hmacShaKeyFor(REFRESH_SECRET.getBytes(StandardCharsets.UTF_8));
      String token = Jwts.builder().subject("7").claim("cli", "tv").signWith(refreshKey).compact();

      // when & then
      assertThatThrownBy(() -> provider.parseUserIdFromRefreshToken(token))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
      assertThatThrownBy(() -> provider.parseUserIdFromRefreshToken(token, LoginClient.APP))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void 앱_갱신은_앱_칸만_검증하고_지우고_새로_저장한다() {
      // given
      JwtTokenProvider provider = createProvider(3600, 604800);

      // when
      TokenPair tokenPair = provider.reissueTokens(7L, LoginClient.APP, "old-app-refresh");

      // then
      InOrder inOrder = Mockito.inOrder(refreshTokenStore);
      inOrder.verify(refreshTokenStore).verify(7L, LoginClient.APP, "old-app-refresh");
      inOrder.verify(refreshTokenStore).delete(7L, LoginClient.APP);
      inOrder
          .verify(refreshTokenStore)
          .save(7L, LoginClient.APP, tokenPair.refreshToken(), 604800);
      then(refreshTokenStore).shouldHaveNoMoreInteractions();
    }
  }

  @Nested
  @DisplayName("웹·앱 칸 분리 — 칸별로 따로 사는지")
  class SlotSeparationTest {

    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();
    private final JwtTokenProvider provider =
        new JwtTokenProvider(ACCESS_SECRET, REFRESH_SECRET, 3600, 604800, 43200, 900, store);

    @Test
    void 앱에서_로그인해도_웹_refresh_로_계속_갱신된다() {
      // given
      String webRefresh = provider.issueTokens(7L).refreshToken();

      // when — 같은 회원이 앱에서 로그인
      provider.issueTokens(7L, LoginClient.APP);

      // then
      assertThat(provider.reissueTokens(7L, webRefresh).accessToken()).isNotBlank();
    }

    @Test
    void 웹에서_다시_로그인해도_앱_refresh_로_계속_갱신된다() {
      // given
      String appRefresh = provider.issueTokens(7L, LoginClient.APP).refreshToken();

      // when
      provider.issueTokens(7L);

      // then
      assertThat(provider.reissueTokens(7L, LoginClient.APP, appRefresh).refreshToken())
          .isNotBlank();
    }

    @Test
    void 앱_칸을_지워도_웹_칸은_남는다() {
      // given
      String webRefresh = provider.issueTokens(7L).refreshToken();
      String appRefresh = provider.issueTokens(7L, LoginClient.APP).refreshToken();

      // when — 앱 로그아웃
      store.delete(7L, LoginClient.APP);

      // then
      assertThat(provider.reissueTokens(7L, webRefresh).accessToken()).isNotBlank();
      assertThatThrownBy(() -> provider.reissueTokens(7L, LoginClient.APP, appRefresh))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void 모든_칸을_지우면_웹도_앱도_갱신되지_않는다() {
      // given
      String webRefresh = provider.issueTokens(7L).refreshToken();
      String appRefresh = provider.issueTokens(7L, LoginClient.APP).refreshToken();

      // when — 탈퇴
      store.deleteAll(7L);

      // then
      assertThatThrownBy(() -> provider.reissueTokens(7L, webRefresh))
          .isInstanceOf(BusinessException.class);
      assertThatThrownBy(() -> provider.reissueTokens(7L, LoginClient.APP, appRefresh))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void 웹_refresh_를_앱_칸으로_갱신하려_하면_거절된다() {
      // given
      String webRefresh = provider.issueTokens(7L).refreshToken();
      provider.issueTokens(7L, LoginClient.APP);

      // when & then — 칸 검증(저장된 앱 토큰과 다름)에서 막힌다
      assertThatThrownBy(() -> provider.reissueTokens(7L, LoginClient.APP, webRefresh))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }
  }

  private static Claims refreshClaims(final String token) {
    SecretKey refreshKey = Keys.hmacShaKeyFor(REFRESH_SECRET.getBytes(StandardCharsets.UTF_8));
    return Jwts.parser().verifyWith(refreshKey).build().parseSignedClaims(token).getPayload();
  }

  /** Redis 저장소와 같은 규칙(회원·칸마다 한 값, 덮어쓰기)의 메모리 대역. */
  private static final class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final Map<String, String> tokens = new HashMap<>();

    @Override
    public void save(
        final Long userId, final LoginClient client, final String token, final long expiration) {
      tokens.put(userId + ":" + client, token);
    }

    @Override
    public void delete(final Long userId, final LoginClient client) {
      tokens.remove(userId + ":" + client);
    }

    @Override
    public void deleteAll(final Long userId) {
      for (LoginClient client : LoginClient.values()) {
        delete(userId, client);
      }
    }

    @Override
    public void verify(final Long userId, final LoginClient client, final String token) {
      if (!token.equals(tokens.get(userId + ":" + client))) {
        throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
      }
    }
  }
}
