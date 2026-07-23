package buncheoleasy.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
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
        ACCESS_SECRET, REFRESH_SECRET, accessExp, refreshExp, adminExp, refreshTokenStore);
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
      then(refreshTokenStore).should().save(userId, tokenPair.refreshToken(), 604800);
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
      inOrder.verify(refreshTokenStore).verify(userId, oldRefreshToken);
      inOrder.verify(refreshTokenStore).delete(userId);
      inOrder.verify(refreshTokenStore).save(userId, tokenPair.refreshToken(), 604800);
    }
  }
}
