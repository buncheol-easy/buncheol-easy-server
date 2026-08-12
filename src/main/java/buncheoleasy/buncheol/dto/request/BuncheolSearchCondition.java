package buncheoleasy.buncheol.dto.request;

import java.util.List;

/**
 * 분철 목록 검색 조건 (groupId/memberId/keyword 는 모두 nullable, null 이면 해당 필터 미적용).
 *
 * <p>{@code keywordGroupIds}/{@code keywordMemberIds} 는 검색어와 이름이 일치하는 그룹·멤버 id 다. 분철 리포지토리가 {@code
 * group} 모듈 엔티티를 직접 조인하지 않고도 그룹명·멤버명으로 분철이 검색되게 하려고, 애플리케이션 레이어에서 미리 해석해 넣는다. 프레젠테이션은 3-인자 생성자로
 * 만들고, 해석 결과는 {@link #withKeywordMatches} 로 채운다.
 */
public record BuncheolSearchCondition(
    Long groupId,
    Long memberId,
    String keyword,
    String normalizedKeyword,
    List<Long> keywordGroupIds,
    List<Long> keywordMemberIds,
    boolean onlyFavoriteGroups,
    List<Long> favoriteGroupIds) {

  // 매칭 0건을 나타내는 값. 현재 Hibernate 는 빈 in-list 를 `1=0` 으로 렌더링하지만, 그 동작에 기대는 대신
  // 어떤 행과도 일치하지 않는 id 를 명시해 JPQL 만 읽고도 의미가 드러나게 한다 (id 는 AUTO_INCREMENT 라 충돌 없음).
  private static final List<Long> NO_MATCH = List.of(-1L);

  // canonical 생성자로 직접 만들어도 IN 절에 빈 컬렉션이 새지 않도록 불변식을 여기서 강제한다.
  public BuncheolSearchCondition {
    keywordGroupIds = orNoMatch(keywordGroupIds);
    keywordMemberIds = orNoMatch(keywordMemberIds);
    favoriteGroupIds = orNoMatch(favoriteGroupIds);
  }

  public BuncheolSearchCondition(final Long groupId, final Long memberId, final String keyword) {
    this(groupId, memberId, keyword, false);
  }

  public BuncheolSearchCondition(
      final Long groupId,
      final Long memberId,
      final String keyword,
      final boolean onlyFavoriteGroups) {
    this(groupId, memberId, keyword, null, NO_MATCH, NO_MATCH, onlyFavoriteGroups, null);
  }

  /**
   * 최애 그룹 필터를 켠 조건. 적용 여부를 id 목록이 아니라 별도 플래그로 들고 다닌다 — 최애가 0개인 사용자의 빈 목록을
   * "필터 없음" 과 같은 값으로 접으면 전체 분철이 노출된다. 빈 목록 자체는 호출 측이 조회 전에 걸러낸다.
   */
  public BuncheolSearchCondition withFavoriteGroups(final List<Long> ids) {
    return new BuncheolSearchCondition(
        groupId,
        memberId,
        keyword,
        normalizedKeyword,
        keywordGroupIds,
        keywordMemberIds,
        true,
        ids);
  }

  public BuncheolSearchCondition withKeywordMatches(
      final String escapedKeyword,
      final String normalizedKeyword,
      final List<Long> matchedGroupIds,
      final List<Long> matchedMemberIds) {
    return new BuncheolSearchCondition(
        groupId,
        memberId,
        escapedKeyword,
        normalizedKeyword,
        orNoMatch(matchedGroupIds),
        orNoMatch(matchedMemberIds),
        onlyFavoriteGroups,
        favoriteGroupIds);
  }

  private static List<Long> orNoMatch(final List<Long> ids) {
    return ids == null || ids.isEmpty() ? NO_MATCH : ids;
  }
}
