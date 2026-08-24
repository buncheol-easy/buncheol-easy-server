package buncheoleasy.buncheol.application.bookmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.application.BuncheolMemberNameResolver;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.request.BookmarkSortOption;
import buncheoleasy.buncheol.dto.response.MyBookmarkedBuncheolResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyBookmarkedBuncheolQueryService 단위 테스트")
class MyBookmarkedBuncheolQueryServiceTest {

  private static final Long USER_ID = 1L;

  @InjectMocks private MyBookmarkedBuncheolQueryService myBookmarkedBuncheolQueryService;

  @Mock private BuncheolBookmarkRepository buncheolBookmarkRepository;
  @Mock private BuncheolRepository buncheolRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private BuncheolImageRepository buncheolImageRepository;
  @Mock private BuncheolMemberNameResolver buncheolMemberNameResolver;
  @Mock private ParticipationRepository participationRepository;
  @Mock private UserFavoriteGroupRepository userFavoriteGroupRepository;

  // 픽스처 마감(2026-06-01)보다 앞선 고정 시각 — RECRUITING 분철은 신규 참여를 받는 상태로 읽힌다.
  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC);

  @Nested
  @DisplayName("내 찜한 분철 목록 조회 테스트")
  class GetMyBookmarkedBuncheolsTest {

    @Test
    void 찜한_분철이_없으면_빈_리스트를_반환한다() {
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of());

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, false, false);

      assertThat(result).isEmpty();
    }

    @Test
    void LATEST_정렬은_찜_등록_순서_그대로_반환되고_멤버_이름은_분철멤버_id_asc_로_포함된다() {
      BuncheolBookmark bm1 = bookmark(500L, USER_ID, 10L);
      BuncheolBookmark bm2 = bookmark(501L, USER_ID, 20L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm1, bm2));

      Instant deadline1 = Instant.parse("2026-06-01T12:00:00Z");
      Instant deadline2 = Instant.parse("2026-06-15T12:00:00Z");
      Buncheol b1 = buncheol(10L, 100L, "분철 A", BuncheolStatus.RECRUITING, deadline1);
      Buncheol b2 = buncheol(20L, 200L, "분철 B", BuncheolStatus.CONFIRMED, deadline2);
      given(buncheolRepository.findAllByIds(List.of(10L, 20L))).willReturn(List.of(b1, b2));

      given(groupRepository.findAllByIds(List.of(100L, 200L)))
          .willReturn(List.of(group(100L, "뉴진스"), group(200L, "에스파")));

      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 20L)))
          .willReturn(List.of(image(10L, "https://cdn/img-a.jpg")));

      // 여러 분철의 활성 점유 슬롯이 평면 리스트로 온다 — 801(분철10 하니)·803(분철20 카리나) 점유.
      given(participationRepository.findActiveBuncheolMemberIds(List.of(10L, 20L)))
          .willReturn(List.of(801L, 803L));
      given(buncheolMemberNameResolver.resolveNames(List.of(10L, 20L), Set.of(801L, 803L)))
          .willReturn(
              new BuncheolMemberNameResolver.MemberNames(
                  Map.of(10L, List.of("하니", "민지"), 20L, List.of("카리나")),
                  Map.of(10L, List.of("민지"))));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, false, false);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).bookmarkId()).isEqualTo(500L);
      assertThat(result.get(0).buncheolId()).isEqualTo(10L);
      assertThat(result.get(0).status()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(result.get(0).groupName()).isEqualTo("뉴진스");
      assertThat(result.get(0).thumbnailUrl()).isEqualTo("https://cdn/img-a.jpg");
      // 801(하니) 가 802(민지) 보다 먼저
      assertThat(result.get(0).memberNames()).containsExactly("하니", "민지");
      // 하니 슬롯은 활성 참여가 점유해 잔여 멤버에서 빠진다
      assertThat(result.get(0).availableMemberNames()).containsExactly("민지");

      assertThat(result.get(1).bookmarkId()).isEqualTo(501L);
      assertThat(result.get(1).buncheolId()).isEqualTo(20L);
      assertThat(result.get(1).status()).isEqualTo(BuncheolStatus.CONFIRMED);
      assertThat(result.get(1).thumbnailUrl()).isNull();
      assertThat(result.get(1).memberNames()).containsExactly("카리나");
      // 전 슬롯이 활성 참여로 점유되면 빈 배열
      assertThat(result.get(1).availableMemberNames()).isEmpty();
    }

    @Test
    void DEADLINE_정렬은_모집중을_마감임박순으로_먼저_보여주고_마감분은_마감일_내림차순으로_잇는다() {
      // 찜 등록 순과 무관하게, 모집중(RECRUITING) 을 deadline ASC 로 먼저,
      // 그 뒤에 마감(CONFIRMED) 을 deadline DESC 로 잇는다.
      BuncheolBookmark bm1 = bookmark(500L, USER_ID, 10L);
      BuncheolBookmark bm2 = bookmark(501L, USER_ID, 20L);
      BuncheolBookmark bm3 = bookmark(502L, USER_ID, 30L);
      BuncheolBookmark bm4 = bookmark(503L, USER_ID, 40L);
      BuncheolBookmark bm5 = bookmark(504L, USER_ID, 50L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm1, bm2, bm3, bm4, bm5));

      Buncheol b1 =
          buncheol(
              10L, 100L, "분철 A", BuncheolStatus.RECRUITING, Instant.parse("2026-07-01T00:00:00Z"));
      Buncheol b2 =
          buncheol(
              20L, 100L, "분철 B", BuncheolStatus.RECRUITING, Instant.parse("2026-06-15T00:00:00Z"));
      Buncheol b3 =
          buncheol(
              30L, 100L, "분철 C", BuncheolStatus.RECRUITING, Instant.parse("2026-06-01T00:00:00Z"));
      // 마감(CONFIRMED) — deadline DESC 로 정렬 (06-20 > 06-10), now 와 무관한 순수 deadline 내림차순
      Buncheol b4 =
          buncheol(
              40L, 100L, "분철 D", BuncheolStatus.CONFIRMED, Instant.parse("2026-06-10T00:00:00Z"));
      Buncheol b5 =
          buncheol(
              50L, 100L, "분철 E", BuncheolStatus.CONFIRMED, Instant.parse("2026-06-20T00:00:00Z"));
      given(buncheolRepository.findAllByIds(List.of(10L, 20L, 30L, 40L, 50L)))
          .willReturn(List.of(b1, b2, b3, b4, b5));

      // 최종 정렬: 모집중 deadline ASC [30, 20, 10] → 마감 deadline DESC [50, 40]
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(30L, 20L, 10L, 50L, 40L)))
          .willReturn(List.of());
      given(participationRepository.findActiveBuncheolMemberIds(List.of(30L, 20L, 10L, 50L, 40L)))
          .willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(List.of(30L, 20L, 10L, 50L, 40L), Set.of()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.DEADLINE, false, false);

      assertThat(result).hasSize(5);
      // 모집중 마감 임박순: C(06-01) → B(06-15) → A(07-01)
      assertThat(result.get(0).buncheolId()).isEqualTo(30L);
      assertThat(result.get(1).buncheolId()).isEqualTo(20L);
      assertThat(result.get(2).buncheolId()).isEqualTo(10L);
      // 그 뒤 마감분 마감일 내림차순: E(06-20) → D(06-10)
      assertThat(result.get(3).buncheolId()).isEqualTo(50L);
      assertThat(result.get(4).buncheolId()).isEqualTo(40L);
      assertThat(result.get(0).memberNames()).isEmpty();
    }

    @Test
    void hideClosed_true_면_RECRUITING_이_아닌_분철은_제외된다() {
      BuncheolBookmark bm1 = bookmark(500L, USER_ID, 10L); // RECRUITING
      BuncheolBookmark bm2 = bookmark(501L, USER_ID, 20L); // CONFIRMED
      BuncheolBookmark bm3 = bookmark(502L, USER_ID, 30L); // CANCELLED
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm1, bm2, bm3));

      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Buncheol b1 = buncheol(10L, 100L, "분철 A", BuncheolStatus.RECRUITING, deadline);
      Buncheol b2 = buncheol(20L, 100L, "분철 B", BuncheolStatus.CONFIRMED, deadline);
      Buncheol b3 = buncheol(30L, 100L, "분철 C", BuncheolStatus.CANCELLED, deadline);
      given(buncheolRepository.findAllByIds(List.of(10L, 20L, 30L)))
          .willReturn(List.of(b1, b2, b3));

      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());
      given(participationRepository.findActiveBuncheolMemberIds(List.of(10L))).willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(List.of(10L), Set.of()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, true, false);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).buncheolId()).isEqualTo(10L);
      assertThat(result.get(0).status()).isEqualTo(BuncheolStatus.RECRUITING);
    }

    @Test
    void hideClosed_true_여도_입금_수집중_분철은_남는다() {
      // 공개 목록 rank0 과 같은 기준(BuncheolStatus.recruitingGroup)이어야 한다 — 개최자가 성사를 확정했다고
      // 찜에서 사라지면, 정작 입금 안내를 기다리는 사람이 그 분철을 잃는다.
      BuncheolBookmark bmRecruiting = bookmark(500L, USER_ID, 10L);
      BuncheolBookmark bmCollecting = bookmark(501L, USER_ID, 20L);
      BuncheolBookmark bmConfirmed = bookmark(502L, USER_ID, 30L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bmRecruiting, bmCollecting, bmConfirmed));

      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      given(buncheolRepository.findAllByIds(List.of(10L, 20L, 30L)))
          .willReturn(
              List.of(
                  buncheol(10L, 100L, "분철 A", BuncheolStatus.RECRUITING, deadline),
                  buncheol(20L, 100L, "분철 B", BuncheolStatus.PAYMENT_COLLECTING, deadline),
                  buncheol(30L, 100L, "분철 C", BuncheolStatus.CONFIRMED, deadline)));

      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 20L)))
          .willReturn(List.of());
      given(participationRepository.findActiveBuncheolMemberIds(List.of(10L, 20L)))
          .willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(List.of(10L, 20L), Set.of()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, true, false);

      assertThat(result).extracting(MyBookmarkedBuncheolResponse::buncheolId).containsExactly(10L, 20L);
    }

    @Test
    void onlyFavoriteGroups_true_면_사용자_최애_그룹의_분철만_포함된다() {
      BuncheolBookmark bmFav = bookmark(500L, USER_ID, 10L);
      BuncheolBookmark bmNonFav = bookmark(501L, USER_ID, 20L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bmFav, bmNonFav));

      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Buncheol bFav = buncheol(10L, 100L, "최애 그룹 분철", BuncheolStatus.RECRUITING, deadline);
      Buncheol bNonFav = buncheol(20L, 200L, "다른 그룹 분철", BuncheolStatus.RECRUITING, deadline);
      given(buncheolRepository.findAllByIds(List.of(10L, 20L))).willReturn(List.of(bFav, bNonFav));

      // 사용자의 최애: 그룹 100 만
      given(userFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(favorite(700L, USER_ID, 100L)));

      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());
      given(participationRepository.findActiveBuncheolMemberIds(List.of(10L))).willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(List.of(10L), Set.of()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, false, true);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).buncheolId()).isEqualTo(10L);
      assertThat(result.get(0).groupName()).isEqualTo("뉴진스");
    }

    @Test
    void 인원미달_취소_CANCELLED_는_노출되고_개최자_취소_HOST_CANCELLED_만_항상_제외된다() {
      BuncheolBookmark bmRecruiting = bookmark(500L, USER_ID, 10L);
      BuncheolBookmark bmCancelled = bookmark(501L, USER_ID, 20L);
      BuncheolBookmark bmHostCancelled = bookmark(502L, USER_ID, 30L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bmRecruiting, bmCancelled, bmHostCancelled));

      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Buncheol recruiting = buncheol(10L, 100L, "모집중 분철", BuncheolStatus.RECRUITING, deadline);
      Buncheol cancelled = buncheol(20L, 100L, "인원미달 취소", BuncheolStatus.CANCELLED, deadline);
      Buncheol hostCancelled = buncheol(30L, 100L, "개최자 취소", BuncheolStatus.HOST_CANCELLED, deadline);
      given(buncheolRepository.findAllByIds(List.of(10L, 20L, 30L)))
          .willReturn(List.of(recruiting, cancelled, hostCancelled));

      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 20L))).willReturn(List.of());
      given(participationRepository.findActiveBuncheolMemberIds(List.of(10L, 20L)))
          .willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(List.of(10L, 20L), Set.of()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, false, false);

      // HOST_CANCELLED(30) 만 제외, RECRUITING(10)·CANCELLED(20) 는 노출 (LATEST = 찜 등록 순)
      assertThat(result)
          .extracting(MyBookmarkedBuncheolResponse::buncheolId)
          .containsExactly(10L, 20L);
    }

    @Test
    void 취소_분철은_활성_참여가_전부_해제되지만_available_은_비어_있다() {
      // 미성사 취소 시 활성 참여가 모두 CANCELLED 로 전이돼 점유 슬롯이 비지만, 취소 분철은 신규 참여를
      // 받지 않으므로 available 은 빈 목록이다 (docs/56 F-2).
      //
      // ⚠️ 이전에는 "활성 참여 부재" 를 그대로 내려 전체 멤버가 잔여로 보였고, 카드가 status 로 다시
      // 걸러 준다는 전제에 기대고 있었다. 상세는 같은 슬롯을 CLOSED 로 내리므로 두 화면이 반대 신호를
      // 줬다 — 판정을 서버로 모아 전제를 없앤다.
      BuncheolBookmark bm = bookmark(500L, USER_ID, 10L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm));

      Buncheol cancelled =
          buncheol(10L, 100L, "인원미달 취소", BuncheolStatus.CANCELLED, Instant.parse("2026-06-01T12:00:00Z"));
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(cancelled));

      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());
      given(participationRepository.findActiveBuncheolMemberIds(List.of(10L))).willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(List.of(10L), Set.of()))
          .willReturn(
              new BuncheolMemberNameResolver.MemberNames(
                  Map.of(10L, List.of("하니", "민지")), Map.of(10L, List.of("하니", "민지"))));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, false, false);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).status()).isEqualTo(BuncheolStatus.CANCELLED);
      assertThat(result.get(0).memberNames()).containsExactly("하니", "민지");
      assertThat(result.get(0).availableMemberNames()).isEmpty();
    }

    @Test
    void 배송비가_모두_0원인_분철만_freeShippingEventTarget_true_로_내려간다() {
      BuncheolBookmark bm1 = bookmark(500L, USER_ID, 10L);
      BuncheolBookmark bm2 = bookmark(501L, USER_ID, 20L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm1, bm2));

      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Buncheol freeShipping =
          buncheol(
              10L,
              100L,
              "배송비 0원 이벤트 분철",
              BuncheolStatus.RECRUITING,
              deadline,
              ShippingFeePolicy.of(0, 0));
      Buncheol paidShipping =
          buncheol(
              20L,
              100L,
              "유료배송 분철",
              BuncheolStatus.RECRUITING,
              deadline,
              ShippingFeePolicy.of(3000, 3000));
      given(buncheolRepository.findAllByIds(List.of(10L, 20L)))
          .willReturn(List.of(freeShipping, paidShipping));

      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 20L)))
          .willReturn(List.of());
      given(participationRepository.findActiveBuncheolMemberIds(List.of(10L, 20L)))
          .willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(List.of(10L, 20L), Set.of()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, false, false);

      assertThat(result)
          .extracting(
              MyBookmarkedBuncheolResponse::buncheolId,
              MyBookmarkedBuncheolResponse::freeShippingEventTarget)
          .containsExactly(tuple(10L, true), tuple(20L, false));
    }

    @Test
    void 필터로_모두_제외되면_빈_리스트를_반환한다() {
      BuncheolBookmark bm = bookmark(500L, USER_ID, 10L);
      given(buncheolBookmarkRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(List.of(bm));

      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Buncheol closed = buncheol(10L, 100L, "분철", BuncheolStatus.CONFIRMED, deadline);
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(closed));

      List<MyBookmarkedBuncheolResponse> result =
          myBookmarkedBuncheolQueryService.getMyBookmarkedBuncheols(
              USER_ID, BookmarkSortOption.LATEST, true, false);

      assertThat(result).isEmpty();
    }
  }

  private UserFavoriteGroup favorite(Long id, Long userId, Long groupId) {
    UserFavoriteGroup favorite = newInstance(UserFavoriteGroup.class);
    setField(favorite, "id", id);
    setField(favorite, "userId", userId);
    setField(favorite, "groupId", groupId);
    return favorite;
  }

  private BuncheolBookmark bookmark(Long id, Long userId, Long buncheolId) {
    BuncheolBookmark bookmark = newInstance(BuncheolBookmark.class);
    setField(bookmark, "id", id);
    setField(bookmark, "userId", userId);
    setField(bookmark, "buncheolId", buncheolId);
    return bookmark;
  }

  private Buncheol buncheol(
      Long id, Long groupId, String title, BuncheolStatus status, Instant deadline) {
    return buncheol(id, groupId, title, status, deadline, ShippingFeePolicy.of(3000, null));
  }

  private Buncheol buncheol(
      Long id,
      Long groupId,
      String title,
      BuncheolStatus status,
      Instant deadline,
      ShippingFeePolicy shippingFeePolicy) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "groupId", groupId);
    setField(buncheol, "title", title);
    setField(buncheol, "status", status);
    setField(buncheol, "deadline", deadline);
    setField(buncheol, "flowType", FlowType.LEGACY);
    setField(buncheol, "shippingFeePolicy", shippingFeePolicy);
    return buncheol;
  }

  private Group group(Long id, String name) {
    Group group = newInstance(Group.class);
    setField(group, "id", id);
    setField(group, "name", name);
    return group;
  }

  private BuncheolImage image(Long buncheolId, String url) {
    BuncheolImage image = newInstance(BuncheolImage.class);
    setField(image, "buncheolId", buncheolId);
    setField(image, "imageUrl", url);
    return image;
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      Field field = findField(target.getClass(), fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
