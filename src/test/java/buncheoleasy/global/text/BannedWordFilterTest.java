package buncheoleasy.global.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import buncheoleasy.user.domain.RandomNicknameGenerator;
import buncheoleasy.user.domain.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BannedWordFilter 단위 테스트")
class BannedWordFilterTest {

  // 실제 목록 파일을 읽는다 — 목록을 고친 뒤 아래 표본이 그대로 통과해야 한다.
  private final BannedWordFilter filter = new BannedWordFilter();

  @Nested
  @DisplayName("사이에 문자를 끼워 피해 가는 글")
  class EvasionTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "시발",
          "시 발",
          "시.발",
          "시_발 분철",
          "씨-발",
          "씨​발", // 제로폭 공백
          "씨ㅤ발", // 한글 채움 문자(빈칸처럼 보인다)
          "씨발", // 풀어 쓴 자모(씨발)
          "ㅅ ㅂ",
          "ＦＵＣＫ", // 전각
          "F u C k",
          "토토 사이트 홍보",
          "포카 분철합니다 병.신.들 오지 마세요"
        })
    void 공백_문장부호_제로폭_전각_대소문자로_끼워도_잡는다(final String text) {
      assertThat(filter.containsBannedWord(text)).isTrue();
    }
  }

  @Nested
  @DisplayName("예외 목록")
  class AllowedPhraseTest {

    // 띄어쓰기를 지우면 시각 뒤 「발송」이 금칙어처럼 이어진다.
    @ParameterizedTest
    @ValueSource(
        strings = {
          "오후 3시 발송 예정",
          "6시 발매",
          "즉시 발송",
          "동시 발매 앨범",
          "다시 발송해 드려요",
          "열시 발송",
          "시발점",
          "기존 나눔 참여자 우선",
          "2병 신청 받아요"
        })
    void 예외_표현_안에_통째로_들어간_일치는_걸지_않는다(final String text) {
      assertThat(filter.containsBannedWord(text)).isFalse();
    }

    @Test
    void 예외_표현이_있어도_다른_곳의_금칙어는_잡는다() {
      assertThat(filter.containsBannedWord("3시 발송합니다 시발")).isTrue();
    }

    // 예외를 짧게 적으면(예: 「다시발」) 문장 끝 「…다」 뒤의 금칙어까지 풀린다.
    @ParameterizedTest
    @ValueSource(strings = {"좋습니다 시발", "발송 예정 시발", "모두 시발", "이상한 시발", "하기 존나 힘들다"})
    void 문장_끝_뒤에_붙은_금칙어는_예외로_풀리지_않는다(final String text) {
      assertThat(filter.containsBannedWord(text)).isTrue();
    }

    @Test
    void 예외_표현이_금칙어의_일부만_덮으면_잡는다() {
      BannedWordFilter custom = new BannedWordFilter(List.of("나쁜말"), List.of("나쁜"));

      assertThat(custom.containsBannedWord("나쁜말")).isTrue();
    }
  }

  @Nested
  @DisplayName("분철 글에 흔한 말")
  class CommonTextTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "세븐틴 SPILL THE FEELS 미공포 분철합니다",
          "스트레이키즈 럭드 특전 포카 분철 (위버스샵)",
          "에스파 섹시한 컨셉 미쳤다 대박",
          "킬링 파트 죽여주는 앨범 분철",
          "엔시티 위시 존잘 존예 포카 개이득",
          "먹튀 없어요 짝퉁 아니에요 정품 인증",
          "마약 김밥처럼 중독되는 노래",
          "몬스타엑스 주헌 형원 기현 민혁 셔누 아이엠",
          "아이브 키치 Kitsch 앨범 개봉",
          "CLASS:y 클라씨 시즌그리팅 분철",
          "BIGBANG FXXK IT, Sexy Nukim",
          "시타지키(下敷き) shitajiki 굿즈",
          "후쿠오카 Fukuoka 콘서트 MD",
          "Dickies 협업 굿즈",
          "공식 스토어 https://weverseshop.io/ko/shop/USD/artists/3/sales/12345",
          "케이타운포유 ktown4u 사운드웨이브 메이크스타 애플뮤직 알라딘 yes24",
          "마감 오후 9시 · 발송은 편의점 반값택배(GS25, CU) 3,000원",
          "입금 확인 후 즉시 발송, 미입금 시 다음 순번",
          "추가 모집합니다! 남은 멤버 2명",
          "포토카드 1장 + 엽서 + 스티커 동봉"
        })
    void 걸지_않는다(final String text) {
      assertThat(filter.containsBannedWord(text)).isFalse();
    }

    // 가입 때 자동으로 붙는 닉네임이 거름에 걸리면 바꾸지 않은 닉네임으로도 저장이 막힐 수 있다. 조합 256개를 넉넉히 뽑는다.
    @Test
    void 자동_닉네임은_걸지_않는다() {
      UserRepository userRepository = mock(UserRepository.class);
      given(userRepository.existsByNickname(anyString())).willReturn(false);
      RandomNicknameGenerator generator = new RandomNicknameGenerator(userRepository);

      for (int i = 0; i < 5_000; i++) {
        String nickname = generator.generate();
        assertThat(filter.containsBannedWord(nickname)).as(nickname).isFalse();
      }
    }
  }

  @Nested
  @DisplayName("입력·목록 경계")
  class EdgeTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "...", "​"})
    void 비었거나_문장부호뿐이면_걸지_않는다(final String text) {
      assertThat(filter.containsBannedWord(text)).isFalse();
    }

    // 정규화해 빈 문자열이 되는 항목을 남기면 모든 글에 걸린다.
    @Test
    void 정규화하면_비는_목록_항목은_버린다() {
      BannedWordFilter custom = new BannedWordFilter(List.of("...", " "), List.of());

      assertThat(custom.containsBannedWord("아무 글")).isFalse();
    }

    @Test
    void 목록_항목도_같은_정규화를_거친다() {
      BannedWordFilter custom = new BannedWordFilter(List.of("Bad Word"), List.of());

      assertThat(custom.containsBannedWord("b-a-d w.o.r.d")).isTrue();
    }
  }
}
