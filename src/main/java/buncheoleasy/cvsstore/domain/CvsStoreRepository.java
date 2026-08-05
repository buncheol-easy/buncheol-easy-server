package buncheoleasy.cvsstore.domain;

import java.util.List;

public interface CvsStoreRepository {

  /**
   * 픽업(수령) 가능한 접수처를 검색한다. 정렬은 {@link CvsStoreCursor} 의 {@code (그룹 순위, id)} — 지점명 일치 그룹을
   * 먼저, 주소만 일치 그룹을 뒤에 잇고 각 그룹은 id 오름차순이다. 각 행에는 소속 그룹 순위가 태깅되어 돌아온다. 브랜드는
   * {@code null} 이면 미적용.
   *
   * @param escapedKeyword {@link buncheoleasy.global.query.LikeEscaper} 로 이스케이프된 검색어. {@code null}
   *     이면 전체 조회(전부 지점명 일치 그룹 취급)
   */
  List<RankedCvsStore> searchPickupStores(
      CvsBrand brand, String escapedKeyword, CvsStoreCursor cursor, int limit);
}
