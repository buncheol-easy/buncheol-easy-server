package buncheoleasy.global.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 다른 회원에게 보이는 자유 입력 칸(분철 제목·설명·구매처, 닉네임)의 금칙어 거름.
 *
 * <p>비교 전에 NFKC·소문자로 맞추고 글자·숫자가 아닌 것(공백·문장부호·제로폭 문자·한글 채움 문자)을 모두 지운 뒤 부분 문자열로 찾는다 — 「시 발」「시.발」
 * 처럼 사이에 끼운 문자로 피해 가지 못하게 하기 위해서다. 그 대가로 단어 경계를 넘는 우연한 일치(예: 시각 뒤 「발송」)가 생기는데, 그런 말은 예외 목록이
 * 감싸면 걸지 않는다.
 *
 * <p>목록은 {@code resources/banned-words/} 의 두 파일이고 기동할 때 한 번 읽는다(없으면 기동 실패). 한 줄에 하나, {@code #} 로 시작하는 줄과
 * 빈 줄은 건너뛴다. 목록 항목도 같은 정규화를 거치므로 띄어 쓰거나 호환 자모(ㅅㅂ)로 적어도 된다.
 */
@Component
public class BannedWordFilter {

  private static final String BANNED_WORDS_PATH = "banned-words/words.txt";
  private static final String ALLOWED_PHRASES_PATH = "banned-words/allowed.txt";

  private final List<String> bannedWords;
  private final List<String> allowedPhrases;

  public BannedWordFilter() {
    this(readEntries(BANNED_WORDS_PATH), readEntries(ALLOWED_PHRASES_PATH));
  }

  BannedWordFilter(final Collection<String> bannedWords, final Collection<String> allowedPhrases) {
    this.bannedWords = normalizeEntries(bannedWords);
    this.allowedPhrases = normalizeEntries(allowedPhrases);
  }

  /** 금칙어가 하나라도 있으면 true. 예외 목록 표현 안에 통째로 들어간 일치는 세지 않는다. null·빈 값은 false. */
  public boolean containsBannedWord(final String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    final String normalized = normalize(text);
    List<int[]> allowedSpans = null;
    for (final String word : bannedWords) {
      int index = normalized.indexOf(word);
      while (index >= 0) {
        if (allowedSpans == null) {
          allowedSpans = findAllowedSpans(normalized);
        }
        if (!isInsideAllowedSpan(allowedSpans, index, index + word.length())) {
          return true;
        }
        index = normalized.indexOf(word, index + 1);
      }
    }
    return false;
  }

  static String normalize(final String text) {
    final String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    final StringBuilder normalized = new StringBuilder(folded.length());
    folded
        .codePoints()
        .filter(codePoint -> Character.isLetterOrDigit(codePoint) && !isHangulFiller(codePoint))
        .forEach(normalized::appendCodePoint);
    return normalized.toString();
  }

  // 한글 채움 문자는 글자(Lo)로 분류돼 위 거름을 통과한다. 화면에는 빈칸으로 보여 띄어쓰기 우회에 쓰인다.
  // NFKC 뒤에는 0x3164·0xFFA0 이 0x1160 으로 바뀌어 있지만, 정규화 순서가 바뀌어도 새지 않게 넷 다 적는다.
  private static boolean isHangulFiller(final int codePoint) {
    return codePoint == 0x115F || codePoint == 0x1160 || codePoint == 0x3164 || codePoint == 0xFFA0;
  }

  private List<int[]> findAllowedSpans(final String normalized) {
    final List<int[]> spans = new ArrayList<>();
    for (final String phrase : allowedPhrases) {
      int index = normalized.indexOf(phrase);
      while (index >= 0) {
        spans.add(new int[] {index, index + phrase.length()});
        index = normalized.indexOf(phrase, index + 1);
      }
    }
    return spans;
  }

  private static boolean isInsideAllowedSpan(
      final List<int[]> allowedSpans, final int start, final int end) {
    for (final int[] span : allowedSpans) {
      if (span[0] <= start && end <= span[1]) {
        return true;
      }
    }
    return false;
  }

  // 정규화하면 빈 문자열이 되는 항목(문장부호만 적은 줄)은 버린다 — 빈 금칙어는 모든 글에 걸린다.
  private static List<String> normalizeEntries(final Collection<String> entries) {
    return entries.stream()
        .map(BannedWordFilter::normalize)
        .filter(entry -> !entry.isEmpty())
        .distinct()
        .toList();
  }

  private static List<String> readEntries(final String path) {
    final ClassPathResource resource = new ClassPathResource(path);
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::strip)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("금칙어 목록을 읽지 못했습니다: " + path, e);
    }
  }
}
