package buncheoleasy.global.page;

import java.util.List;

/** 커서 페이지네이션 응답 봉투. */
public record CursorResponse<T>(List<T> items, String nextCursor, boolean hasNext) {

  public static <T> CursorResponse<T> empty() {
    return new CursorResponse<>(List.of(), null, false);
  }
}
