package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.application.payback.ShippingFeePaybackPolicy;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.global.query.LikeEscaper;
import buncheoleasy.global.query.SearchText;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 분철 목록(둘러보기/검색) 조회.
 *
 * <p>비로그인 호출도 허용한다 ({@code userId == null} 이면 모든 항목의 {@code bookmarked} 가 false). 정렬은 모집중을 최신
 * 개최순(createdAt DESC) 으로 먼저, 그 뒤에 마감분을 마감 임박순(deadline DESC) 으로 잇는다({@link BuncheolListCursor} 참고). hasNext
 * 판별을 위해 size+1 fetch 패턴을 사용한다.
 *
 * <p>로그인 사용자가 keyword 검색을 한 경우 {@link BuncheolSearchedEvent} 를 발행해 비동기 listener 가 최근 검색 이력을 갱신하도록 한다.
 * groupId/memberId 단독 호출은 프론트가 같은 사용자 입력에서 파생시킨 부수 요청이라 이력으로 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BuncheolListQueryService {

  private static final int MIN_SIZE = 1;
  private static final int MAX_SIZE = 50;

  private final BuncheolRepository buncheolRepository;
  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final UserFavoriteGroupRepository userFavoriteGroupRepository;
  private final BuncheolBookmarkRepository buncheolBookmarkRepository;
  private final BuncheolImageRepository buncheolImageRepository;
  private final BuncheolMemberNameResolver buncheolMemberNameResolver;
  private final BuncheolMemberRepository buncheolMemberRepository;
  private final ParticipationRepository participationRepository;
  private final ShippingFeePaybackPolicy shippingFeePaybackPolicy;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public CursorResponse<BuncheolSummaryResponse> search(
      final Long userId,
      final BuncheolSearchCondition condition,
      final BuncheolListCursor cursor,
      final int requestedSize) {
    final int safeSize = clampSize(requestedSize);
    final String trimmedKeyword = trimKeyword(condition.keyword());
    final BuncheolSearchCondition withFavorites = resolveFavoriteGroups(userId, condition);

    // 최애 필터를 켰는데 등록된 최애가 없으면 매칭될 분철이 없다. 조회를 건너뛴다.
    if (condition.onlyFavoriteGroups() && withFavorites == null) {
      return CursorResponse.empty();
    }

    final BuncheolSearchCondition resolved =
        resolveKeywordMatches(withFavorites, trimmedKeyword);

    final List<Buncheol> fetched = buncheolRepository.search(resolved, cursor, safeSize + 1);
    final boolean hasNext = fetched.size() > safeSize;
    final List<Buncheol> visible = hasNext ? fetched.subList(0, safeSize) : fetched;

    // 결과 0건이어도 "검색 시도" 자체를 이력으로 남긴다 (검색창의 일반적인 UX 와 동일).
    publishSearchedEventIfMeaningful(userId, trimmedKeyword);

    if (visible.isEmpty()) {
      return CursorResponse.empty();
    }

    final List<Long> buncheolIds = visible.stream().map(Buncheol::getId).toList();
    final List<Long> groupIds = visible.stream().map(Buncheol::getGroupId).distinct().toList();

    final Map<Long, String> groupNameById =
        groupRepository.findAllByIds(groupIds).stream()
            .collect(Collectors.toMap(Group::getId, Group::getName));
    final Map<Long, String> thumbnailByBuncheolId =
        buncheolImageRepository.findThumbnailsByBuncheolIds(buncheolIds).stream()
            .collect(Collectors.toMap(BuncheolImage::getBuncheolId, BuncheolImage::getImageUrl));
    // 활성 참여가 점유한 슬롯을 빼면 "아직 안 팔린" 멤버. 전체/잔여 이름을 한 번의 조회로 함께 만든다.
    final Set<Long> takenBuncheolMemberIds =
        Set.copyOf(participationRepository.findActiveBuncheolMemberIds(buncheolIds));
    final BuncheolMemberNameResolver.MemberNames memberNames =
        buncheolMemberNameResolver.resolveNames(buncheolIds, takenBuncheolMemberIds);
    final Set<Long> bookmarkedBuncheolIds =
        userId == null
            ? Set.of()
            : buncheolBookmarkRepository.findBookmarkedBuncheolIds(userId, buncheolIds);
    // 오픈 이벤트 무료 분철 배지 판정용. 이벤트 비활성 환경에서는 조회 자체를 건너뛴다 (전 카드 false).
    final Set<Long> freeSlotBuncheolIds =
        shippingFeePaybackPolicy.isEnabled()
            ? Set.copyOf(buncheolMemberRepository.findAllFreeSlotBuncheolIds(buncheolIds))
            : Set.of();

    final List<BuncheolSummaryResponse> items =
        visible.stream()
            .map(
                b ->
                    new BuncheolSummaryResponse(
                        b.getId(),
                        b.getTitle(),
                        b.getStatus(),
                        b.getFlowType(),
                        b.getDeadline(),
                        b.getMinHeadcount(),
                        bookmarkedBuncheolIds.contains(b.getId()),
                        groupNameById.get(b.getGroupId()),
                        thumbnailByBuncheolId.get(b.getId()),
                        memberNames.all().getOrDefault(b.getId(), List.of()),
                        memberNames.available().getOrDefault(b.getId(), List.of()),
                        shippingFeePaybackPolicy.isEventTargetBuncheol(
                            b.getFlowType(), freeSlotBuncheolIds.contains(b.getId()))))
            .toList();

    final String nextCursor = hasNext ? BuncheolListCursor.from(visible.getLast()).encode() : null;
    return new CursorResponse<>(items, nextCursor, hasNext);
  }

  /**
   * 최애 필터가 켜져 있으면 사용자의 최애 그룹 id 를 해석해 조건에 싣는다. 비로그인이거나 최애가 0개면 {@code null} 을
   * 반환해 호출 측이 빈 결과로 단축하게 한다 — 빈 목록을 그대로 넘기면 "필터 없음" 과 구분되지 않아 전체가 노출된다.
   */
  private BuncheolSearchCondition resolveFavoriteGroups(
      final Long userId, final BuncheolSearchCondition condition) {
    if (!condition.onlyFavoriteGroups()) {
      return condition;
    }
    if (userId == null) {
      return null;
    }

    final List<Long> favoriteGroupIds =
        userFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
            .map(UserFavoriteGroup::getGroupId)
            .toList();

    return favoriteGroupIds.isEmpty() ? null : condition.withFavoriteGroups(favoriteGroupIds);
  }

  /**
   * 검색어와 이름이 일치하는 그룹·멤버를 미리 해석해 조건에 싣는다. 분철 리포지토리가 {@code group} 모듈 엔티티를 조인하지 않도록 조합을 이 레이어에서
   * 한다 — 그룹·멤버 테이블이 작아 조회 2회는 무시할 만하다 (부분일치 LIKE 라 인덱스 시크는 못 하고 스캔이다).
   */
  private BuncheolSearchCondition resolveKeywordMatches(
      final BuncheolSearchCondition condition, final String trimmedKeyword) {
    final String normalizedKeyword = SearchText.normalizeForLike(trimmedKeyword);
    if (normalizedKeyword == null) {
      // 조건을 새로 만들면 앞서 해석한 최애 필터가 사라진다. 키워드 관련 필드만 갈아끼운다.
      return condition.withKeywordMatches(
          LikeEscaper.escape(trimmedKeyword), null, List.of(), List.of());
    }
    return condition.withKeywordMatches(
        LikeEscaper.escape(trimmedKeyword),
        normalizedKeyword,
        groupRepository.findIdsByNormalizedKeyword(normalizedKeyword),
        groupMemberRepository.findIdsByNormalizedName(normalizedKeyword));
  }

  private int clampSize(final int requested) {
    return Math.max(MIN_SIZE, Math.min(requested, MAX_SIZE));
  }

  private void publishSearchedEventIfMeaningful(
      final Long userId, final String trimmedKeyword) {
    if (userId == null || trimmedKeyword == null) {
      return;
    }
    eventPublisher.publishEvent(new BuncheolSearchedEvent(userId, trimmedKeyword));
  }

  private static String trimKeyword(final String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    return keyword.trim();
  }
}
