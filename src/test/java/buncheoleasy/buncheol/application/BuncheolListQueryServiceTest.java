package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackPolicy;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolListQueryService 단위 테스트")
class BuncheolListQueryServiceTest {

  @InjectMocks private BuncheolListQueryService buncheolListQueryService;

  @Mock private BuncheolRepository buncheolRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private GroupMemberRepository groupMemberRepository;
  @Mock private BuncheolBookmarkRepository buncheolBookmarkRepository;
  @Mock private BuncheolImageRepository buncheolImageRepository;
  @Mock private BuncheolMemberNameResolver buncheolMemberNameResolver;
  @Mock private BuncheolMemberRepository buncheolMemberRepository;
  @Mock private ParticipationRepository participationRepository;
  @Mock private ShippingFeePaybackPolicy shippingFeePaybackPolicy;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Nested
  @DisplayName("검색 결과 반환")
  class SearchResultTest {

    @Test
    void 결과가_요청_size_와_같거나_적으면_hasNext_false_이고_nextCursor_가_null() {
      Buncheol b1 = buncheol(10L, 100L, "분철 A", Instant.parse("2026-05-15T08:00:00Z"));
      Buncheol b2 = buncheol(11L, 100L, "분철 B", Instant.parse("2026-05-14T08:00:00Z"));
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of(b1, b2));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 11L)))
          .willReturn(List.of(image(10L, "https://cdn.example.com/a.jpg")));
      given(buncheolMemberNameResolver.resolveNames(eq(List.of(10L, 11L)), any()))
          .willReturn(
              new BuncheolMemberNameResolver.MemberNames(
                  Map.of(10L, List.of("민지"), 11L, List.of("하니")), Map.of()));
      given(buncheolBookmarkRepository.findBookmarkedBuncheolIds(1L, List.of(10L, 11L)))
          .willReturn(Set.of(10L));

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              1L, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 20);

      assertThat(result.items()).hasSize(2);
      assertThat(result.items().get(0).id()).isEqualTo(10L);
      assertThat(result.items().get(0).status()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(result.items().get(0).flowType()).isEqualTo(FlowType.LEGACY);
      assertThat(result.items().get(0).bookmarked()).isTrue();
      assertThat(result.items().get(0).groupName()).isEqualTo("뉴진스");
      assertThat(result.items().get(0).thumbnailUrl()).isEqualTo("https://cdn.example.com/a.jpg");
      assertThat(result.items().get(0).minHeadcount()).isEqualTo(3);
      assertThat(result.items().get(0).memberNames()).containsExactly("민지");
      assertThat(result.items().get(1).bookmarked()).isFalse();
      assertThat(result.items().get(1).thumbnailUrl()).isNull();
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
    }

    @Test
    void memberNames_는_전체_멤버_availableMemberNames_는_안_팔린_멤버만_내려준다() {
      Buncheol b1 = buncheol(10L, 100L, "분철 A", Instant.parse("2026-05-15T08:00:00Z"));
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of(b1));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());
      // 슬롯 501(민지)은 활성 참여로 점유됨 → available 에서 제외.
      given(participationRepository.findActiveBuncheolMemberIds(List.of(10L)))
          .willReturn(List.of(501L));
      given(buncheolMemberNameResolver.resolveNames(List.of(10L), Set.of(501L)))
          .willReturn(
              new BuncheolMemberNameResolver.MemberNames(
                  Map.of(10L, List.of("민지", "하니")), Map.of(10L, List.of("하니"))));
      given(buncheolBookmarkRepository.findBookmarkedBuncheolIds(1L, List.of(10L)))
          .willReturn(Set.of());

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              1L, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 20);

      assertThat(result.items().get(0).memberNames()).containsExactly("민지", "하니");
      assertThat(result.items().get(0).availableMemberNames()).containsExactly("하니");
    }

    @Test
    void 전_슬롯이_매진이면_availableMemberNames_는_빈_리스트다() {
      Buncheol b1 = buncheol(10L, 100L, "분철 A", Instant.parse("2026-05-15T08:00:00Z"));
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of(b1));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());
      given(participationRepository.findActiveBuncheolMemberIds(List.of(10L)))
          .willReturn(List.of(501L, 502L));
      // 매진: all 에는 전체 멤버가 있지만 available 맵에는 해당 분철 key 자체가 없다 → getOrDefault 폴백.
      given(buncheolMemberNameResolver.resolveNames(List.of(10L), Set.of(501L, 502L)))
          .willReturn(
              new BuncheolMemberNameResolver.MemberNames(
                  Map.of(10L, List.of("민지", "하니")), Map.of()));
      given(buncheolBookmarkRepository.findBookmarkedBuncheolIds(1L, List.of(10L)))
          .willReturn(Set.of());

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              1L, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 20);

      assertThat(result.items().get(0).memberNames()).containsExactly("민지", "하니");
      assertThat(result.items().get(0).availableMemberNames()).isEmpty();
    }

    @Test
    void size_plus1_fetch_되어_hasNext_true_시_마지막은_drop_되고_nextCursor_는_visible_의_마지막_항목() {
      Instant t1 = Instant.parse("2026-05-15T08:00:00Z");
      Instant t2 = Instant.parse("2026-05-14T08:00:00Z");
      Instant t3 = Instant.parse("2026-05-13T08:00:00Z"); // drop 대상
      Buncheol b1 = buncheol(10L, 100L, "분철 A", t1);
      Buncheol b2 = buncheol(11L, 100L, "분철 B", t2);
      Buncheol bDropped = buncheol(12L, 100L, "분철 C", t3);
      // requestedSize=2 → repo 는 size+1=3 으로 호출, 3개를 반환받음 → hasNext=true, visible=2개
      given(buncheolRepository.search(any(), any(), anyInt()))
          .willReturn(List.of(b1, b2, bDropped));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 11L))).willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(eq(List.of(10L, 11L)), any()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));
      given(buncheolBookmarkRepository.findBookmarkedBuncheolIds(1L, List.of(10L, 11L)))
          .willReturn(Set.of());

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              1L, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 2);

      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
      verify(buncheolRepository).search(any(), any(), limitCaptor.capture());
      assertThat(limitCaptor.getValue()).isEqualTo(3);

      assertThat(result.items()).hasSize(2);
      assertThat(result.items().get(0).id()).isEqualTo(10L);
      assertThat(result.items().get(1).id()).isEqualTo(11L);
      assertThat(result.hasNext()).isTrue();
      // RECRUITING 그룹(rank 0) → nextCursor = "0_<createdAt>_<id>"
      assertThat(result.nextCursor()).isEqualTo("0_" + t2 + "_11");
    }

    @Test
    void 마지막_visible_이_마감_분철이면_nextCursor_는_rank1_과_deadline_으로_인코딩된다() {
      Instant deadline1 = Instant.parse("2026-06-20T00:00:00Z");
      Instant deadline2 = Instant.parse("2026-06-10T00:00:00Z");
      Instant deadline3 = Instant.parse("2026-06-05T00:00:00Z"); // drop 대상
      Buncheol c1 = confirmedBuncheol(20L, 100L, "마감 A", deadline1);
      Buncheol c2 = confirmedBuncheol(21L, 100L, "마감 B", deadline2);
      Buncheol dropped = confirmedBuncheol(22L, 100L, "마감 C", deadline3);
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of(c1, c2, dropped));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(20L, 21L))).willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(eq(List.of(20L, 21L)), any()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));
      given(buncheolBookmarkRepository.findBookmarkedBuncheolIds(1L, List.of(20L, 21L)))
          .willReturn(Set.of());

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              1L, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 2);

      assertThat(result.items().get(0).status()).isEqualTo(BuncheolStatus.CONFIRMED);
      assertThat(result.hasNext()).isTrue();
      // CONFIRMED 그룹(rank 1) → nextCursor = "1_<deadline>_<id>"
      assertThat(result.nextCursor()).isEqualTo("1_" + deadline2 + "_21");
    }

    @Test
    void visible_이_비어있으면_그룹_멤버_북마크_이미지_조회를_모두_생략한다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              1L, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 20);

      assertThat(result.items()).isEmpty();
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
      verifyNoInteractions(
          groupRepository,
          buncheolMemberNameResolver,
          buncheolBookmarkRepository,
          buncheolImageRepository);
    }
  }

  @Nested
  @DisplayName("비로그인 (userId=null) 처리")
  class AnonymousTest {

    @Test
    void userId_가_null_이면_bookmarked_가_모두_false_이고_북마크_조회_생략() {
      Buncheol b1 = buncheol(10L, 100L, "분철 A", Instant.parse("2026-05-15T08:00:00Z"));
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of(b1));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());
      given(buncheolMemberNameResolver.resolveNames(eq(List.of(10L)), any()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              null, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 20);

      assertThat(result.items()).hasSize(1);
      assertThat(result.items().get(0).bookmarked()).isFalse();
      verify(buncheolBookmarkRepository, never()).findBookmarkedBuncheolIds(anyLong(), anyList());
    }
  }

  @Nested
  @DisplayName("대표이미지 매핑")
  class ThumbnailTest {

    @Test
    void 이미지가_없는_분철은_thumbnailUrl_이_null_로_매핑된다() {
      Buncheol b1 = buncheol(10L, 100L, "분철 A", Instant.parse("2026-05-15T08:00:00Z"));
      Buncheol b2 = buncheol(11L, 100L, "분철 B", Instant.parse("2026-05-14T08:00:00Z"));
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of(b1, b2));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      // 10L 만 이미지 등록, 11L 은 미등록
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 11L)))
          .willReturn(List.of(image(10L, "https://cdn.example.com/a.jpg")));
      given(buncheolMemberNameResolver.resolveNames(eq(List.of(10L, 11L)), any()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              null, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 20);

      assertThat(result.items().get(0).thumbnailUrl()).isEqualTo("https://cdn.example.com/a.jpg");
      assertThat(result.items().get(1).thumbnailUrl()).isNull();
    }

    @Test
    void 모든_분철에_이미지가_있으면_각각_thumbnailUrl_이_채워진다() {
      Buncheol b1 = buncheol(10L, 100L, "분철 A", Instant.parse("2026-05-15T08:00:00Z"));
      Buncheol b2 = buncheol(11L, 100L, "분철 B", Instant.parse("2026-05-14T08:00:00Z"));
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of(b1, b2));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 11L)))
          .willReturn(
              List.of(
                  image(10L, "https://cdn.example.com/a.jpg"),
                  image(11L, "https://cdn.example.com/b.jpg")));
      given(buncheolMemberNameResolver.resolveNames(eq(List.of(10L, 11L)), any()))
          .willReturn(new BuncheolMemberNameResolver.MemberNames(Map.of(), Map.of()));

      CursorResponse<BuncheolSummaryResponse> result =
          buncheolListQueryService.search(
              null, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 20);

      assertThat(result.items().get(0).thumbnailUrl()).isEqualTo("https://cdn.example.com/a.jpg");
      assertThat(result.items().get(1).thumbnailUrl()).isEqualTo("https://cdn.example.com/b.jpg");
    }
  }

  @Nested
  @DisplayName("입력 정규화")
  class NormalizationTest {

    @Test
    void size_는_1_미만이면_1_로_50_초과면_50_으로_클램프되어_size_plus1_fetch_된다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 0);
      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 999);

      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
      verify(buncheolRepository, times(2)).search(any(), any(), limitCaptor.capture());
      assertThat(limitCaptor.getAllValues()).containsExactly(2, 51); // (1+1), (50+1)
    }

    @Test
    void keyword_가_blank_면_null_로_정규화되어_repo_에_전달된다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, "   "), BuncheolListCursor.firstPage(), 20);

      ArgumentCaptor<BuncheolSearchCondition> captor =
          ArgumentCaptor.forClass(BuncheolSearchCondition.class);
      verify(buncheolRepository).search(captor.capture(), any(), anyInt());
      assertThat(captor.getValue().keyword()).isNull();
    }

    @Test
    void 그룹명_멤버명_매칭_id_가_조건에_실린다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());
      given(groupRepository.findIdsByNormalizedKeyword("아이브")).willReturn(List.of(7L));
      given(groupMemberRepository.findIdsByNormalizedName("아이브")).willReturn(List.of(70L, 71L));

      buncheolListQueryService.search(
          1L,
          new BuncheolSearchCondition(null, null, "아이 브"),
          BuncheolListCursor.firstPage(),
          20);

      ArgumentCaptor<BuncheolSearchCondition> captor =
          ArgumentCaptor.forClass(BuncheolSearchCondition.class);
      verify(buncheolRepository).search(captor.capture(), any(), anyInt());
      assertThat(captor.getValue().normalizedKeyword()).isEqualTo("아이브");
      assertThat(captor.getValue().keywordGroupIds()).containsExactly(7L);
      assertThat(captor.getValue().keywordMemberIds()).containsExactly(70L, 71L);
    }

    @Test
    void 매칭이_없으면_어떤_행과도_일치하지_않는_id_가_실린다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());
      given(groupRepository.findIdsByNormalizedKeyword("없는그룹")).willReturn(List.of());
      given(groupMemberRepository.findIdsByNormalizedName("없는그룹")).willReturn(List.of());

      buncheolListQueryService.search(
          1L,
          new BuncheolSearchCondition(null, null, "없는그룹"),
          BuncheolListCursor.firstPage(),
          20);

      ArgumentCaptor<BuncheolSearchCondition> captor =
          ArgumentCaptor.forClass(BuncheolSearchCondition.class);
      verify(buncheolRepository).search(captor.capture(), any(), anyInt());
      assertThat(captor.getValue().keywordGroupIds()).doesNotContain(7L).isNotEmpty();
      assertThat(captor.getValue().keywordMemberIds()).isNotEmpty();
    }

    @Test
    void 구두점만_입력하면_정규화_검색어가_null_이라_제목_매칭이_비활성화된다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, "---"), BuncheolListCursor.firstPage(), 20);

      ArgumentCaptor<BuncheolSearchCondition> captor =
          ArgumentCaptor.forClass(BuncheolSearchCondition.class);
      verify(buncheolRepository).search(captor.capture(), any(), anyInt());
      assertThat(captor.getValue().normalizedKeyword()).isNull();
      // 원문은 남아 설명(description) 원문 검색에는 그대로 쓰인다.
      assertThat(captor.getValue().keyword()).isEqualTo("---");
      verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void keyword_는_trim_되어_repo_에_전달된다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, "  뉴진스  "), BuncheolListCursor.firstPage(), 20);

      ArgumentCaptor<BuncheolSearchCondition> captor =
          ArgumentCaptor.forClass(BuncheolSearchCondition.class);
      verify(buncheolRepository).search(captor.capture(), any(), anyInt());
      assertThat(captor.getValue().keyword()).isEqualTo("뉴진스");
    }
  }

  @Nested
  @DisplayName("최근 검색어 이벤트 발행")
  class SearchedEventTest {

    @Test
    void 비로그인이면_이벤트를_발행하지_않는다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          null, new BuncheolSearchCondition(null, null, "ive원영"), BuncheolListCursor.firstPage(), 20);

      verifyNoInteractions(eventPublisher);
    }

    @Test
    void 키워드가_없으면_이벤트를_발행하지_않는다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 20);

      verifyNoInteractions(eventPublisher);
    }

    @Test
    void 키워드가_blank_면_이벤트를_발행하지_않는다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, "   "), BuncheolListCursor.firstPage(), 20);

      verifyNoInteractions(eventPublisher);
    }

    @Test
    void groupId_만_있으면_이벤트를_발행하지_않는다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(100L, null, null), BuncheolListCursor.firstPage(), 20);

      verifyNoInteractions(eventPublisher);
    }

    @Test
    void memberId_만_있으면_이벤트를_발행하지_않는다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, 200L, null), BuncheolListCursor.firstPage(), 20);

      verifyNoInteractions(eventPublisher);
    }

    @Test
    void 로그인_사용자가_키워드로_검색하면_trim_된_원문이_이벤트에_실려_발행된다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, "  ive원영  "), BuncheolListCursor.firstPage(), 20);

      ArgumentCaptor<BuncheolSearchedEvent> captor =
          ArgumentCaptor.forClass(BuncheolSearchedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().userId()).isEqualTo(1L);
      assertThat(captor.getValue().rawKeyword()).isEqualTo("ive원영");
    }

    @Test
    void 이벤트의_rawKeyword_는_LIKE_escape_되지_않은_원문이다() {
      given(buncheolRepository.search(any(), any(), anyInt())).willReturn(List.of());

      buncheolListQueryService.search(
          1L, new BuncheolSearchCondition(null, null, "100%"), BuncheolListCursor.firstPage(), 20);

      ArgumentCaptor<BuncheolSearchCondition> repoCaptor =
          ArgumentCaptor.forClass(BuncheolSearchCondition.class);
      verify(buncheolRepository).search(repoCaptor.capture(), any(), anyInt());
      assertThat(repoCaptor.getValue().keyword()).isEqualTo("100\\%"); // repo 에는 escape 된 값

      ArgumentCaptor<BuncheolSearchedEvent> eventCaptor =
          ArgumentCaptor.forClass(BuncheolSearchedEvent.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue().rawKeyword()).isEqualTo("100%"); // 이벤트엔 원문
    }
  }

  // 공개 목록 정렬에서 RECRUITING 그룹은 createdAt 기준이므로, 픽스처는 RECRUITING 으로 둔다.
  // (BuncheolListCursor.from 이 status 로 그룹 순위·정렬 시각을 정한다 → nextCursor 형식 검증과 직결)
  private Buncheol buncheol(Long id, Long groupId, String title, Instant createdAt) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "groupId", groupId);
    setField(buncheol, "title", title);
    setField(buncheol, "deadline", Instant.parse("2026-06-01T12:00:00Z"));
    setField(buncheol, "minHeadcount", 3);
    setField(buncheol, "status", BuncheolStatus.RECRUITING);
    setField(buncheol, "flowType", FlowType.LEGACY);
    // CreatedAtEntity#createdAt 은 부모 필드. setField 가 super 까지 탐색.
    setField(buncheol, "createdAt", createdAt);
    return buncheol;
  }

  // 마감(CONFIRMED) 그룹 픽스처. 정렬·커서가 deadline 기준이므로 deadline 을 주입한다.
  private Buncheol confirmedBuncheol(Long id, Long groupId, String title, Instant deadline) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "groupId", groupId);
    setField(buncheol, "title", title);
    setField(buncheol, "deadline", deadline);
    setField(buncheol, "status", BuncheolStatus.CONFIRMED);
    setField(buncheol, "createdAt", Instant.parse("2026-05-01T00:00:00Z"));
    return buncheol;
  }

  private Group group(Long id, String name) {
    Group group = newInstance(Group.class);
    setField(group, "id", id);
    setField(group, "name", name);
    return group;
  }

  private BuncheolImage image(Long buncheolId, String imageUrl) {
    BuncheolImage image = newInstance(BuncheolImage.class);
    setField(image, "buncheolId", buncheolId);
    setField(image, "imageUrl", imageUrl);
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
