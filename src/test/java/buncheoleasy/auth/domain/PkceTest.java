package buncheoleasy.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Pkce 단위 테스트")
class PkceTest {

  // RFC 7636 Appendix B 예시 값
  private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

  @Nested
  @DisplayName("matches")
  class MatchesTest {

    @Test
    void RFC_7636_예시_verifier_는_예시_challenge_와_맞는다() {
      assertThat(Pkce.matches(VERIFIER, CHALLENGE)).isTrue();
    }

    @Test
    void verifier_가_한_글자만_달라도_맞지_않는다() {
      String other = VERIFIER.substring(0, VERIFIER.length() - 1) + "Y";

      assertThat(Pkce.matches(other, CHALLENGE)).isFalse();
    }

    @Test
    void challenge_를_그대로_verifier_로_내면_맞지_않는다() {
      assertThat(Pkce.matches(CHALLENGE, CHALLENGE)).isFalse();
    }

    @Test
    void null_은_맞지_않는다() {
      assertThat(Pkce.matches(null, CHALLENGE)).isFalse();
      assertThat(Pkce.matches(VERIFIER, null)).isFalse();
    }
  }

  @Nested
  @DisplayName("isValidChallenge")
  class IsValidChallengeTest {

    @Test
    void base64url_43자는_올바르다() {
      assertThat(Pkce.isValidChallenge(CHALLENGE)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {
          "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-c",
          "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cMx",
          "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-c=",
          "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw+cM",
          "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSst.-cM"
        })
    void 길이나_글자가_다르면_올바르지_않다(final String challenge) {
      assertThat(Pkce.isValidChallenge(challenge)).isFalse();
    }
  }
}
