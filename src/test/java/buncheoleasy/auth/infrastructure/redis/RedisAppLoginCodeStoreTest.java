package buncheoleasy.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.auth.domain.AppLoginGrant;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisAppLoginCodeStore 단위 테스트")
class RedisAppLoginCodeStoreTest {

  private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisAppLoginCodeStore store;

  @BeforeEach
  void setUp() {
    store = new RedisAppLoginCodeStore(redisTemplate);
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
  }

  @Test
  void APPCODE_키에_회원_새가입_challenge_를_TTL_과_함께_저장한다() {
    // when
    store.save("abc", new AppLoginGrant(7L, CHALLENGE, true), Duration.ofSeconds(120));

    // then
    then(valueOperations).should().set("APPCODE:abc", "7:1:" + CHALLENGE, Duration.ofSeconds(120));
  }

  @Test
  void 꺼낼_때는_GETDEL_한_번으로_읽고_지운다() {
    // given
    given(valueOperations.getAndDelete("APPCODE:abc")).willReturn("7:0:" + CHALLENGE);

    // when & then
    assertThat(store.consume("abc")).contains(new AppLoginGrant(7L, CHALLENGE, false));
    then(valueOperations).should(never()).get("APPCODE:abc");
    then(redisTemplate).should(never()).delete("APPCODE:abc");
  }

  @Test
  void 없는_코드는_비어_있다() {
    // given
    given(valueOperations.getAndDelete("APPCODE:abc")).willReturn(null);

    // when & then
    assertThat(store.consume("abc")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"7", "7:1", "x:1:challenge", "7:2:challenge", ""})
  void 값_모양이_다르면_비어_있다(final String value) {
    // given
    given(valueOperations.getAndDelete("APPCODE:abc")).willReturn(value);

    // when & then
    assertThat(store.consume("abc")).isEmpty();
  }
}
