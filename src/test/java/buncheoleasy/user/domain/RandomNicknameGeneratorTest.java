package buncheoleasy.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RandomNicknameGenerator 단위 테스트")
class RandomNicknameGeneratorTest {

  @InjectMocks private RandomNicknameGenerator generator;

  @Mock private UserRepository userRepository;

  @Test
  void 닉네임_형식_규칙에_맞는_조합값을_생성한다() {
    // given
    given(userRepository.existsByNickname(anyString())).willReturn(false);

    // when
    String nickname = generator.generate();

    // then: Nickname VO 규칙(1~20자, 한글/영문/숫자)과 호환되어야 한다
    assertThat(nickname).matches("^[가-힣a-zA-Z0-9]{1,20}$");
    assertThat(Nickname.of(nickname).value()).isEqualTo(nickname);
  }

  @Test
  void 중복이_계속되면_Guest_방식으로_fallback_한다() {
    // given
    given(userRepository.existsByNickname(anyString())).willReturn(true);

    // when
    String nickname = generator.generate();

    // then
    assertThat(nickname).startsWith("Guest");
    assertThat(Nickname.of(nickname).value()).isEqualTo(nickname);
  }
}
