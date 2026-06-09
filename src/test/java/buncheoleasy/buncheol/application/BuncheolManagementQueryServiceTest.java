package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.BuncheolManagementOptionResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementResponse;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.delivery.domain.DeliveryStatus;
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
@DisplayName("BuncheolManagementQueryService 단위 테스트")
class BuncheolManagementQueryServiceTest {

  @InjectMocks private BuncheolManagementQueryService buncheolManagementQueryService;

  @Mock private BuncheolRepository buncheolRepository;
  @Mock private BuncheolMemberRepository buncheolMemberRepository;
  @Mock private ParticipationRepository participationRepository;
  @Mock private DeliveryRepository deliveryRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private GroupMemberRepository groupMemberRepository;

  private static final Long BUNCHEOL_ID = 10L;
  private static final Long GROUP_ID = 100L;
  private static final Long HOST_ID = 777L;
  private static final Long OTHER_USER = 888L;
  private static final Long WINNER_USER = 555L;
  private static final Instant DEADLINE = Instant.parse("2026-06-01T12:00:00Z");
  private static final Instant DUE_AT = Instant.parse("2026-06-02T12:00:00Z");
  private static final Instant REPORTED_AT = Instant.parse("2026-06-01T15:00:00Z");
  private static final Instant CONFIRMED_AT = Instant.parse("2026-06-01T18:00:00Z");

  @Nested
  @DisplayName("개최자 분철 관리 화면 조회")
  class GetManagementTest {

    @Test
    void 존재하지_않는_분철은_BUNCHEOL_NOT_FOUND() {
      given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.empty());

      assertThatThrownBy(
              () -> buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
    }

    @Test
    void 호스트가_아닌_유저가_호출하면_BUNCHEOL_NO_PERMISSION() {
      Buncheol buncheol = buncheol(BuncheolStatus.RECRUITING);
      given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.of(buncheol));

      assertThatThrownBy(
              () -> buncheolManagementQueryService.getManagement(BUNCHEOL_ID, OTHER_USER))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      // 권한 검증 실패 시 후속 조회는 일어나지 않아야 한다.
      verify(groupRepository, never()).findById(any());
      verify(buncheolMemberRepository, never()).findAllByBuncheolIdOrderByIdAsc(any());
    }

    @Test
    void 분철은_있지만_그룹이_없으면_GROUP_NOT_FOUND() {
      Buncheol buncheol = buncheol(BuncheolStatus.RECRUITING);
      given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.of(buncheol));
      given(groupRepository.findById(GROUP_ID)).willReturn(Optional.empty());

      assertThatThrownBy(
              () -> buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 모집중_입찰_없음_옵션은_currentHighestBid_null_winner_null() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진", "yujin.png")));
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.id()).isEqualTo(BUNCHEOL_ID);
      assertThat(response.title()).isEqualTo("호두 자랑");
      assertThat(response.groupName()).isEqualTo("IVE");
      assertThat(response.purchaseSite()).isEqualTo("호두네");
      assertThat(response.status()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(response.deadline()).isEqualTo(DEADLINE);
      assertThat(response.optionCount()).isEqualTo(1);
      assertThat(response.totalParticipationCount()).isZero();
      assertThat(response.options()).hasSize(1);
      BuncheolManagementOptionResponse option = response.options().get(0);
      assertThat(option.buncheolMemberId()).isEqualTo(101L);
      assertThat(option.memberName()).isEqualTo("안유진");
      assertThat(option.memberImage()).isEqualTo("yujin.png");
      assertThat(option.participationCount()).isZero();
      assertThat(option.currentHighestBid()).isNull();
      assertThat(option.winner()).isNull();
    }

    @Test
    void 모집중_활성_입찰만_있는_옵션은_winner_null_이고_currentHighestBid_는_최고가() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진", "yujin.png")));
      // bidAmount DESC, id ASC 정렬된 입력
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  activeBid(901L, 101L, OTHER_USER, 90_000L),
                  activeBid(701L, 101L, OTHER_USER, 70_000L)));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.totalParticipationCount()).isEqualTo(2);
      BuncheolManagementOptionResponse option = response.options().get(0);
      assertThat(option.participationCount()).isEqualTo(2);
      assertThat(option.currentHighestBid()).isEqualTo(90_000L);
      assertThat(option.winner()).isNull();
    }

    @Test
    void 결제대기_AWAITING_PAYMENT_낙찰자는_winner로_노출되고_배송필드는_null() {
      // AWAITING_PAYMENT 는 현재 결제 대상이라 winner 로 노출하되, 배송 스냅샷은 CONFIRMED 전이라 아직 null 이다.
      stubBasicBuncheol(BuncheolStatus.CLOSED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진", "yujin.png")));
      Participation awaiting = awaitingPaymentBid(601L, 101L, WINNER_USER, 90_000L);
      setField(awaiting, "dueAt", DUE_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(awaiting));
      // CONFIRMED 가 없으니 Delivery 조회는 빈 id 목록으로 들어간다.
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      BuncheolManagementOptionResponse option = response.options().get(0);
      assertThat(option.participationCount()).isEqualTo(1);
      assertThat(option.currentHighestBid()).isEqualTo(90_000L);
      assertThat(option.winner()).isNotNull();
      assertThat(option.winner().participationId()).isEqualTo(601L);
      assertThat(option.winner().paymentStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(option.winner().bidAmount()).isEqualTo(90_000L);
      assertThat(option.winner().paymentDueAt()).isEqualTo(DUE_AT);
      assertThat(option.winner().paymentReportedAt()).isNull();
      assertThat(option.winner().paymentConfirmedAt()).isNull();
      // 배송 스냅샷은 아직 없다.
      assertThat(option.winner().deliveryId()).isNull();
      assertThat(option.winner().shippingMethod()).isNull();
      assertThat(option.winner().trackingNumber()).isNull();
      assertThat(option.winner().deliveryStatus()).isNull();
    }

    @Test
    void 입금신고_PAYMENT_REPORTED_낙찰자는_winner로_노출되고_신고시각이_채워진다() {
      stubBasicBuncheol(BuncheolStatus.CLOSED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진", "yujin.png")));
      Participation reported =
          participation(601L, 101L, WINNER_USER, 90_000L, ParticipationStatus.PAYMENT_REPORTED);
      setField(reported, "dueAt", DUE_AT);
      setField(reported, "paymentReportedAt", REPORTED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(reported));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      BuncheolManagementOptionResponse option = response.options().get(0);
      assertThat(option.winner()).isNotNull();
      assertThat(option.winner().participationId()).isEqualTo(601L);
      assertThat(option.winner().paymentStatus()).isEqualTo(ParticipationStatus.PAYMENT_REPORTED);
      assertThat(option.winner().paymentReportedAt()).isEqualTo(REPORTED_AT);
      assertThat(option.winner().paymentConfirmedAt()).isNull();
      assertThat(option.winner().deliveryId()).isNull();
    }

    @Test
    void 낙찰자가_있는_옵션은_winner_와_Delivery_정보가_채워진다() {
      stubBasicBuncheol(BuncheolStatus.PAID);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진", "yujin.png")));
      Participation confirmed = confirmedBid(601L, 101L, WINNER_USER, 90_000L);
      setField(confirmed, "dueAt", DUE_AT);
      setField(confirmed, "paymentReportedAt", REPORTED_AT);
      setField(confirmed, "paymentConfirmedAt", CONFIRMED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(confirmed));
      Delivery delivery =
          delivery(
              5001L, 601L, "GS25 강남역점", "유진팬", "010-1234-5678", null, DeliveryStatus.SNAPSHOTTED);
      given(deliveryRepository.findAllByParticipationIds(List.of(601L)))
          .willReturn(List.of(delivery));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      BuncheolManagementOptionResponse option = response.options().get(0);
      assertThat(option.participationCount()).isEqualTo(1);
      assertThat(option.currentHighestBid()).isEqualTo(90_000L);
      assertThat(option.winner()).isNotNull();
      // 결제 필드: CONFIRMED 낙찰자
      assertThat(option.winner().participationId()).isEqualTo(601L);
      assertThat(option.winner().paymentStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(option.winner().bidAmount()).isEqualTo(90_000L);
      assertThat(option.winner().paymentReportedAt()).isEqualTo(REPORTED_AT);
      assertThat(option.winner().paymentConfirmedAt()).isEqualTo(CONFIRMED_AT);
      // 배송 필드: CONFIRMED 스냅샷
      assertThat(option.winner().deliveryId()).isEqualTo(5001L);
      assertThat(option.winner().shippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
      assertThat(option.winner().storeName()).isEqualTo("GS25 강남역점");
      assertThat(option.winner().receiverNickname()).isEqualTo("유진팬");
      assertThat(option.winner().receiverPhoneNumber()).isEqualTo("010-1234-5678");
      assertThat(option.winner().trackingNumber()).isNull();
      assertThat(option.winner().deliveryStatus()).isEqualTo(DeliveryStatus.SNAPSHOTTED);
    }

    @Test
    void 운송장이_등록된_옵션은_trackingNumber_와_status_SHIPPING_을_노출한다() {
      stubBasicBuncheol(BuncheolStatus.SETTLING);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진", "yujin.png")));
      Participation confirmed = confirmedBid(601L, 101L, WINNER_USER, 90_000L);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(confirmed));
      Delivery delivery =
          delivery(
              5001L,
              601L,
              "GS25 강남역점",
              "유진팬",
              "010-1234-5678",
              "1234567890",
              DeliveryStatus.SHIPPING);
      given(deliveryRepository.findAllByParticipationIds(List.of(601L)))
          .willReturn(List.of(delivery));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.options().get(0).winner().trackingNumber()).isEqualTo("1234567890");
      assertThat(response.options().get(0).winner().deliveryStatus())
          .isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    void 여러_옵션_여러_낙찰자_시나리오를_옵션별로_정확히_매핑한다() {
      stubBasicBuncheol(BuncheolStatus.PAID);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  buncheolMember(101L, 1001L),
                  buncheolMember(102L, 1002L),
                  buncheolMember(103L, 1003L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L, 1002L, 1003L)))
          .willReturn(
              List.of(
                  groupMember(1001L, "안유진", "yujin.png"),
                  groupMember(1002L, "레이", "rei.png"),
                  groupMember(1003L, "리즈", "leeseo.png")));
      // 슬롯 101: 낙찰자 1명 + 활성 1명 → 최고가는 활성 입찰의 100_000, 낙찰자는 90_000
      // 슬롯 102: 낙찰자 없음, 활성 1명
      // 슬롯 103: 입찰 없음
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(
              List.of(
                  activeBid(801L, 101L, OTHER_USER, 100_000L),
                  confirmedBid(601L, 101L, WINNER_USER, 90_000L),
                  activeBid(901L, 102L, OTHER_USER, 60_000L)));
      Delivery deliveryFor601 =
          delivery(5001L, 601L, "GS25 잠실점", "유진팬", "010-0000-0000", null, DeliveryStatus.SNAPSHOTTED);
      given(deliveryRepository.findAllByParticipationIds(List.of(601L)))
          .willReturn(List.of(deliveryFor601));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.optionCount()).isEqualTo(3);
      assertThat(response.totalParticipationCount()).isEqualTo(3);

      BuncheolManagementOptionResponse yujin = response.options().get(0);
      assertThat(yujin.memberName()).isEqualTo("안유진");
      assertThat(yujin.participationCount()).isEqualTo(2);
      // 마감 후에도 미낙찰 활성 입찰(100,000)이 낙찰가(90,000)보다 높으면 currentHighestBid 로 노출된다.
      assertThat(yujin.currentHighestBid()).isEqualTo(100_000L);
      assertThat(yujin.winner()).isNotNull();
      assertThat(yujin.winner().deliveryId()).isEqualTo(5001L);

      BuncheolManagementOptionResponse rei = response.options().get(1);
      assertThat(rei.memberName()).isEqualTo("레이");
      assertThat(rei.participationCount()).isEqualTo(1);
      assertThat(rei.currentHighestBid()).isEqualTo(60_000L);
      assertThat(rei.winner()).isNull();

      BuncheolManagementOptionResponse leeseo = response.options().get(2);
      assertThat(leeseo.memberName()).isEqualTo("리즈");
      assertThat(leeseo.participationCount()).isZero();
      assertThat(leeseo.currentHighestBid()).isNull();
      assertThat(leeseo.winner()).isNull();
    }

    @Test
    void 옵션_슬롯이_없는_분철도_정상_응답한다() {
      stubBasicBuncheol(BuncheolStatus.CLOSED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.optionCount()).isZero();
      assertThat(response.options()).isEmpty();
      assertThat(response.totalParticipationCount()).isZero();
      // 멤버 슬롯이 없으면 GroupMember 조회는 일어나지 않는다.
      verify(groupMemberRepository, never()).findAllByGroupIdAndIds(any(), anyList());
    }
  }

  private void stubBasicBuncheol(final BuncheolStatus status) {
    Buncheol buncheol = buncheol(status);
    given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.of(buncheol));
    given(groupRepository.findById(GROUP_ID)).willReturn(Optional.of(group(GROUP_ID, "IVE")));
  }

  private Buncheol buncheol(final BuncheolStatus status) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", BUNCHEOL_ID);
    setField(buncheol, "hostId", HOST_ID);
    setField(buncheol, "groupId", GROUP_ID);
    setField(buncheol, "title", "호두 자랑");
    setField(buncheol, "description", "분철 설명");
    setField(buncheol, "purchaseSite", "호두네");
    setField(buncheol, "deadline", DEADLINE);
    setField(buncheol, "status", status);
    setField(buncheol, "shippingFeePolicy", ShippingFeePolicy.of(3000, null));
    return buncheol;
  }

  private BuncheolMember buncheolMember(final Long id, final Long memberId) {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", id);
    setField(member, "buncheolId", BUNCHEOL_ID);
    setField(member, "memberId", memberId);
    setField(member, "bidMinPrice", 50_000L);
    return member;
  }

  private Group group(final Long id, final String name) {
    Group group = newInstance(Group.class);
    setField(group, "id", id);
    setField(group, "name", name);
    return group;
  }

  private GroupMember groupMember(final Long id, final String name, final String image) {
    GroupMember member = newInstance(GroupMember.class);
    setField(member, "id", id);
    setField(member, "name", name);
    setField(member, "image", image);
    return member;
  }

  private Participation activeBid(
      final Long id, final Long buncheolMemberId, final Long participantId, final long bidAmount) {
    return participation(id, buncheolMemberId, participantId, bidAmount, ParticipationStatus.ACTIVE_BID);
  }

  private Participation confirmedBid(
      final Long id, final Long buncheolMemberId, final Long participantId, final long bidAmount) {
    return participation(id, buncheolMemberId, participantId, bidAmount, ParticipationStatus.CONFIRMED);
  }

  private Participation awaitingPaymentBid(
      final Long id, final Long buncheolMemberId, final Long participantId, final long bidAmount) {
    return participation(
        id, buncheolMemberId, participantId, bidAmount, ParticipationStatus.AWAITING_PAYMENT);
  }

  private Participation participation(
      final Long id,
      final Long buncheolMemberId,
      final Long participantId,
      final long bidAmount,
      final ParticipationStatus status) {
    Participation p = newInstance(Participation.class);
    setField(p, "id", id);
    setField(p, "buncheolId", BUNCHEOL_ID);
    setField(p, "buncheolMemberId", buncheolMemberId);
    setField(p, "participantId", participantId);
    setField(p, "bidAmount", bidAmount);
    setField(p, "status", status);
    return p;
  }

  private Delivery delivery(
      final Long id,
      final Long participationId,
      final String storeName,
      final String nickname,
      final String phone,
      final String trackingNumber,
      final DeliveryStatus status) {
    Delivery delivery = newInstance(Delivery.class);
    setField(delivery, "id", id);
    setField(delivery, "participationId", participationId);
    setField(delivery, "shippingMethod", ShippingMethod.GS25_HALF);
    setField(delivery, "storeName", storeName);
    setField(delivery, "receiverNickname", nickname);
    setField(delivery, "receiverPhoneNumber", phone);
    setField(delivery, "trackingNumber", trackingNumber);
    setField(delivery, "status", status);
    return delivery;
  }

  private static <T> T newInstance(final Class<T> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setField(final Object target, final String fieldName, final Object value) {
    try {
      Field field = findField(target.getClass(), fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Field findField(final Class<?> type, final String fieldName)
      throws NoSuchFieldException {
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
