package buncheoleasy.user.domain;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 가입 시 기본 닉네임을 "형용사+명사+숫자" 조합으로 생성한다(예: "용감한까마귀12"). 기존 "Guest"+UUID 더미 닉네임을 대체하는 카카오싱크 전환 결정
 * 사항(멘토 제안, docs/20). 중복이면 재추첨하고, 상한을 넘기면 기존 Guest 방식으로 fallback 한다.
 */
@Component
@RequiredArgsConstructor
public class RandomNicknameGenerator {

  private static final int MAX_ATTEMPTS = 10;
  private static final int RANDOM_SUFFIX_BOUND = 1000;
  private static final List<String> ADJECTIVES =
      List.of(
          "용감한", "귀여운", "명랑한", "다정한", "씩씩한", "포근한", "반짝이는", "조용한", "재빠른", "느긋한", "엉뚱한", "새침한",
          "든든한", "상냥한", "활발한", "수줍은");
  private static final List<String> NOUNS =
      List.of(
          "까마귀", "고슴도치", "펭귄", "수달", "너구리", "다람쥐", "고래", "부엉이", "여우", "판다", "토끼", "물범", "햄스터",
          "알파카", "치타", "돌고래");

  private final UserRepository userRepository;
  private final SecureRandom random = new SecureRandom();

  public String generate() {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      String candidate =
          ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
              + NOUNS.get(random.nextInt(NOUNS.size()))
              + random.nextInt(RANDOM_SUFFIX_BOUND);

      if (!userRepository.existsByNickname(candidate)) {
        return candidate;
      }
    }
    // 조합이 전부 충돌하는 극단 상황 — 유일성이 보장되는 기존 방식으로 fallback.
    return "Guest" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }
}
