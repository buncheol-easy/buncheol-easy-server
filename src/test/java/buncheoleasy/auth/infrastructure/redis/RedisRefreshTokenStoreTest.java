package buncheoleasy.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.auth.domain.LoginClient;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRefreshTokenStore 단위 테스트")
class RedisRefreshTokenStoreTest {

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  @Nested
  @DisplayName("save/delete 테스트")
  class SaveDeleteTest {

    @Test
    void 웹_칸은_앱_칸이_생기기_전과_같은_RT_prefix_키로_저장한다() {
      // given
      RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);
      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      // when
      store.save(1L, LoginClient.WEB, "refresh-token", 3600);

      // then
      then(valueOperations).should().set("RT:1", "refresh-token", 3600, TimeUnit.SECONDS);
    }

    @Test
    void 웹_칸_삭제는_RT_prefix_키만_삭제한다() {
      // given
      RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);

      // when
      store.delete(1L, LoginClient.WEB);

      // then
      then(redisTemplate).should().delete("RT:1");
    }

    @Test
    void 앱_칸은_RT_APP_prefix_키로_저장한다() {
      // given
      RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);
      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      // when
      store.save(1L, LoginClient.APP, "app-refresh-token", 3600);

      // then
      then(valueOperations).should().set("RT:APP:1", "app-refresh-token", 3600, TimeUnit.SECONDS);
    }

    @Test
    void 앱_칸_삭제는_RT_APP_prefix_키만_삭제한다() {
      // given
      RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);

      // when
      store.delete(1L, LoginClient.APP);

      // then
      then(redisTemplate).should().delete("RT:APP:1");
      then(redisTemplate).shouldHaveNoMoreInteractions();
    }

    @Test
    void 모든_칸_삭제는_웹과_앱_키를_함께_삭제한다() {
      // given
      RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);

      // when
      store.deleteAll(1L);

      // then
      then(redisTemplate).should().delete(List.of("RT:1", "RT:APP:1"));
    }
  }

  @Nested
  @DisplayName("verify 테스트")
  class VerifyTest {

    @Test
    void 저장된_토큰과_일치하면_통과한다() {
      // given
      RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get("RT:1")).willReturn("token");

      // when & then
      assertThatCode(() -> store.verify(1L, LoginClient.WEB, "token")).doesNotThrowAnyException();
    }

    @Test
    void 저장된_토큰이_없으면_예외가_발생한다() {
      // given
      RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get("RT:1")).willReturn(null);

      // when & then
      assertThatThrownBy(() -> store.verify(1L, LoginClient.WEB, "token"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void 저장된_토큰과_다르면_예외가_발생한다() {
      // given
      RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get("RT:1")).willReturn("another-token");

      // when & then
      assertThatThrownBy(() -> store.verify(1L, LoginClient.WEB, "token"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void 앱_칸은_앱_키에_저장된_값과_비교한다() {
      // given
      RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get("RT:APP:1")).willReturn("app-token");

      // when & then
      assertThatCode(() -> store.verify(1L, LoginClient.APP, "app-token"))
          .doesNotThrowAnyException();
    }
  }
}
