package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolMemberBidResponse;
import buncheoleasy.buncheol.dto.response.MyBidResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolDetailQueryService 단위 테스트")
class BuncheolDetailQueryServiceTest {

  @InjectMocks private BuncheolDetailQueryService buncheolDetailQueryService;

  @Mock private BuncheolRepository buncheolRepository;
  @Mock private BuncheolImageRepository buncheolImageRepository;
  @Mock private BuncheolMemberRepository buncheolMemberRepository;
  @Mock private ParticipationRepository participationRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private GroupMemberRepository groupMemberRepository;

  private static final Long BUNCHEOL_ID = 10L;
  private static final Long GROUP_ID = 100L;
  private static final Long ME = 999L;
  private static final Long OTHER_USER = 888L;
  private static final Instant DEADLINE = Instant.parse("2026-06-01T12:00:00Z");

  @Nested
  @DisplayName("분철 단건 상세 조회")
  class GetDetailTest {

    @Test
    void 존재하지_않는_분철은_BUNCHEOL_NOT_FOUND() {
      given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.empty());

      assertThatThrownBy(() -> buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
    }

    @Test
    void 분철은_있지만_그룹이_없으면_GROUP_NOT_FOUND() {
      Buncheol buncheol =
          buncheol(BUNCHEOL_ID, GROUP_ID, "제목", BuncheolStatus.RECRUITING,
              ShippingFeePolicy.of(3000, null));
      given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.of(buncheol));
      given(groupRepository.findById(GROUP_ID)).willReturn(Optional.empty());

      assertThatThrownBy(() -> buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 비로그인_호출시_myParticipation_은_null() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, 4000));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(image(1L, "img-a.jpg"), image(2L, "img-b.jpg")));
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, BUNCHEOL_ID, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지", "minji.png")));
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, null);

      assertThat(response.myParticipation()).isNull();
      assertThat(response.imageUrls()).containsExactly("img-a.jpg", "img-b.jpg");
      assertThat(response.shippingOptions())
          .extracting("method", "fee")
          .containsExactly(
              tuple(ShippingMethod.GS25_HALF, 3000), tuple(ShippingMethod.CU_HALF, 4000));
    }

    @Test
    void 로그인_미참여_상태에서_myParticipation_은_빈_요약() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, BUNCHEOL_ID, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지", "minji.png")));
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(activeBid(501L, 101L, OTHER_USER, 50_000L)));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.myParticipation()).isNotNull();
      assertThat(response.myParticipation().participatedMemberCount()).isZero();
      assertThat(response.myParticipation().bids()).isEmpty();
    }

    @Test
    void 멤버별_top3_와_count_와_rank_를_계산한다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  buncheolMember(101L, BUNCHEOL_ID, 1001L),
                  buncheolMember(102L, BUNCHEOL_ID, 1002L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L, 1002L)))
          .willReturn(
              List.of(
                  groupMember(1001L, "민지", "minji.png"), groupMember(1002L, "해린", "haerin.png")));
      // 슬롯 101 활성 입찰 4건 (DESC 정렬된 상태로 들어옴): 90, 70, 50(me), 40
      // 슬롯 102 활성 입찰 1건: 35(me)
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  activeBid(901L, 101L, OTHER_USER, 90_000L),
                  activeBid(701L, 101L, OTHER_USER, 70_000L),
                  activeBid(601L, 101L, ME, 50_000L),
                  activeBid(401L, 101L, OTHER_USER, 40_000L),
                  activeBid(351L, 102L, ME, 35_000L)));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members()).hasSize(2);
      BuncheolMemberBidResponse minji = response.members().get(0);
      assertThat(minji.buncheolMemberId()).isEqualTo(101L);
      assertThat(minji.memberName()).isEqualTo("민지");
      assertThat(minji.topBidAmounts()).containsExactly(90_000L, 70_000L, 50_000L);
      assertThat(minji.activeParticipantCount()).isEqualTo(4);
      BuncheolMemberBidResponse haerin = response.members().get(1);
      assertThat(haerin.buncheolMemberId()).isEqualTo(102L);
      assertThat(haerin.topBidAmounts()).containsExactly(35_000L);
      assertThat(haerin.activeParticipantCount()).isEqualTo(1);

      assertThat(response.myParticipation().participatedMemberCount()).isEqualTo(2);
      assertThat(response.myParticipation().bids())
          .extracting(
              MyBidResponse::participationId,
              MyBidResponse::buncheolMemberId,
              MyBidResponse::bidAmount,
              MyBidResponse::rank)
          .containsExactly(tuple(601L, 101L, 50_000L, 3), tuple(351L, 102L, 35_000L, 1));
    }

    @Test
    void 활성_참여_4건_이상이어도_topBidAmounts_는_3개로_제한된다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, BUNCHEOL_ID, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지", "minji.png")));
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  activeBid(901L, 101L, OTHER_USER, 90_000L),
                  activeBid(801L, 101L, OTHER_USER, 80_000L),
                  activeBid(701L, 101L, OTHER_USER, 70_000L),
                  activeBid(601L, 101L, OTHER_USER, 60_000L),
                  activeBid(501L, 101L, OTHER_USER, 50_000L)));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members().get(0).topBidAmounts())
          .containsExactly(90_000L, 80_000L, 70_000L);
      assertThat(response.members().get(0).activeParticipantCount()).isEqualTo(5);
    }

    @Test
    void 멤버_슬롯이_없는_분철도_정상_응답한다() {
      stubBasicBuncheol(BuncheolStatus.CLOSED, ShippingFeePolicy.of(null, 4000));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.members()).isEmpty();
      assertThat(response.shippingOptions())
          .extracting("method", "fee")
          .containsExactly(tuple(ShippingMethod.CU_HALF, 4000));
    }

    @Test
    void CANCELLED_분철도_200_으로_status_를_포함해_반환한다() {
      stubBasicBuncheol(BuncheolStatus.CANCELLED, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, null);

      assertThat(response.status()).isEqualTo(BuncheolStatus.CANCELLED);
    }

    @Test
    void 동일_금액_입찰이_들어와도_입력_순서_기준으로_rank_가_결정적이다() {
      // 도메인 규칙상 동일 (member, bid_amount) 입찰은 불가하지만, 어댑터 정렬 (bidAmount DESC, id ASC) 가
      // 깨졌을 때 서비스 단의 rank 계산이 입력 순서를 그대로 따라가는지 확인한다.
      stubBasicBuncheol(BuncheolStatus.RECRUITING, ShippingFeePolicy.of(3000, null));
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, BUNCHEOL_ID, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지", "minji.png")));
      // 어댑터가 보장하는 (bidAmount DESC, id ASC) 순서를 stub 으로 모사한다.
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  activeBid(901L, 101L, OTHER_USER, 80_000L),
                  activeBid(902L, 101L, ME, 80_000L),
                  activeBid(903L, 101L, OTHER_USER, 70_000L)));

      BuncheolDetailResponse response = buncheolDetailQueryService.getDetail(BUNCHEOL_ID, ME);

      assertThat(response.myParticipation().bids())
          .singleElement()
          .satisfies(
              bid -> {
                assertThat(bid.participationId()).isEqualTo(902L);
                assertThat(bid.rank()).isEqualTo(2);
              });
    }
  }

  private void stubBasicBuncheol(final BuncheolStatus status, final ShippingFeePolicy policy) {
    Buncheol buncheol = buncheol(BUNCHEOL_ID, GROUP_ID, "뉴진스 1집 분철", status, policy);
    given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.of(buncheol));
    given(groupRepository.findById(GROUP_ID)).willReturn(Optional.of(group(GROUP_ID, "뉴진스")));
  }

  private Buncheol buncheol(
      Long id,
      Long groupId,
      String title,
      BuncheolStatus status,
      ShippingFeePolicy shippingFeePolicy) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "groupId", groupId);
    setField(buncheol, "title", title);
    setField(buncheol, "description", "분철 설명");
    setField(buncheol, "purchaseSite", "https://store.example.com");
    setField(buncheol, "deadline", DEADLINE);
    setField(buncheol, "status", status);
    setField(buncheol, "shippingFeePolicy", shippingFeePolicy);
    return buncheol;
  }

  private BuncheolMember buncheolMember(Long id, Long buncheolId, Long memberId) {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", id);
    setField(member, "buncheolId", buncheolId);
    setField(member, "memberId", memberId);
    return member;
  }

  private BuncheolImage image(Long id, String url) {
    BuncheolImage image = newInstance(BuncheolImage.class);
    setField(image, "id", id);
    setField(image, "imageUrl", url);
    return image;
  }

  private Group group(Long id, String name) {
    Group group = newInstance(Group.class);
    setField(group, "id", id);
    setField(group, "name", name);
    return group;
  }

  private GroupMember groupMember(Long id, String name, String image) {
    GroupMember member = newInstance(GroupMember.class);
    setField(member, "id", id);
    setField(member, "name", name);
    setField(member, "image", image);
    return member;
  }

  private Participation activeBid(
      Long id, Long buncheolMemberId, Long participantId, long bidAmount) {
    Participation p = newInstance(Participation.class);
    setField(p, "id", id);
    setField(p, "buncheolId", BUNCHEOL_ID);
    setField(p, "buncheolMemberId", buncheolMemberId);
    setField(p, "participantId", participantId);
    setField(p, "bidAmount", bidAmount);
    setField(p, "status", ParticipationStatus.ACTIVE_BID);
    return p;
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
