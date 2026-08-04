package buncheoleasy.cvsstore.application;

import buncheoleasy.cvsstore.domain.CvsBrand;
import buncheoleasy.cvsstore.domain.CvsStore;
import buncheoleasy.cvsstore.domain.CvsStoreCursor;
import buncheoleasy.cvsstore.domain.CvsStoreRepository;
import buncheoleasy.cvsstore.dto.response.CvsStoreResponse;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.global.query.LikeEscaper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송지 등록 화면의 편의점 접수처 검색.
 *
 * <p>비로그인 호출도 허용하는 공개 마스터 데이터 조회다. 배송지(수령지) 후보만 의미가 있으므로 픽업 가능 점포로 한정한다. 정렬은
 * {@link CvsStoreCursor} 의 (그룹, id) — 지점명 일치 매장을 주소만 일치 매장보다 먼저 노출한다. hasNext 판별은 size+1
 * fetch 패턴을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class CvsStoreService {

  private static final int MIN_SIZE = 1;
  private static final int MAX_SIZE = 50;

  private final CvsStoreRepository cvsStoreRepository;

  @Transactional(readOnly = true)
  public CursorResponse<CvsStoreResponse> searchStores(
      final String brandName, final String keyword, final String cursor, final int requestedSize) {
    final CvsBrand brand = CvsBrand.fromFilter(brandName);
    final String trimmedKeyword = trimKeyword(keyword);
    final String escapedKeyword = LikeEscaper.escape(trimmedKeyword);
    final CvsStoreCursor parsedCursor = CvsStoreCursor.parse(cursor);
    final int safeSize = clampSize(requestedSize);

    final List<CvsStore> fetched =
        cvsStoreRepository.searchPickupStores(brand, escapedKeyword, parsedCursor, safeSize + 1);
    final boolean hasNext = fetched.size() > safeSize;
    final List<CvsStore> visible = hasNext ? fetched.subList(0, safeSize) : fetched;

    if (visible.isEmpty()) {
      return CursorResponse.empty();
    }

    final List<CvsStoreResponse> items = visible.stream().map(CvsStoreResponse::from).toList();
    final String nextCursor =
        hasNext ? CvsStoreCursor.from(visible.getLast(), trimmedKeyword).encode() : null;
    return new CursorResponse<>(items, nextCursor, hasNext);
  }

  private static int clampSize(final int requested) {
    return Math.max(MIN_SIZE, Math.min(requested, MAX_SIZE));
  }

  private static String trimKeyword(final String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    return keyword.trim();
  }
}
