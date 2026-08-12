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
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.dto.response.BuncheolManagementParticipantResponse;
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
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
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
  @Mock private UserRepository userRepository;

  private static final Long BUNCHEOL_ID = 10L;
  private static final Long GROUP_ID = 100L;
  private static final Long HOST_ID = 777L;
  private static final Long OTHER_USER = 888L;
  private static final Long PARTICIPANT_USER = 555L;
  private static final Instant DEADLINE = Instant.parse("2026-06-01T12:00:00Z");
  private static final Instant DUE_AT = Instant.parse("2026-06-02T12:00:00Z");
  private static final Instant CONFIRMED_AT = Instant.parse("2026-06-01T18:00:00Z");
  private static final RefundAccount REFUND_ACCOUNT = RefundAccount.of("국민", "12345678", "홍길동");

  @Nested
  @DisplayName("개최자 분철 관리 화면 조회")
  class GetManagementTest {

    @Test
    void 존재하지_않는_분철은_BUNCHEOL_NOT_FOUND() {
      given(buncheolRepository.findById(BUNCHEOL_ID)).willReturn(Optional.empty());

      assertThatThrownBy(() -> buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID))
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

      assertThatThrownBy(() -> buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 참여자가_없는_분철은_빈_참여자_목록을_반환한다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(Collections.emptyList())).willReturn(List.of());

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.id()).isEqualTo(BUNCHEOL_ID);
      assertThat(response.title()).isEqualTo("호두 자랑");
      assertThat(response.groupName()).isEqualTo("IVE");
      assertThat(response.purchaseSite()).isEqualTo("호두네");
      assertThat(response.status()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(response.deadline()).isEqualTo(DEADLINE);
      assertThat(response.minHeadcount()).isEqualTo(3);
      assertThat(response.memberCount()).isEqualTo(1);
      assertThat(response.confirmedCount()).isZero();
      assertThat(response.participants()).isEmpty();
    }

    @Test
    void 입금확인중_참여는_환불계좌와_dueAt이_노출되고_배송은_null() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      Participation awaiting =
          participation(601L, 101L, PARTICIPANT_USER, 53_000L, ParticipationStatus.AWAITING_PAYMENT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(awaiting));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      // CONFIRMED 가 없으니 Delivery 조회는 빈 id 목록으로 들어간다.
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.confirmedCount()).isZero();
      assertThat(response.participants()).hasSize(1);
      BuncheolManagementParticipantResponse participant = response.participants().get(0);
      assertThat(participant.participationId()).isEqualTo(601L);
      assertThat(participant.participantNickname()).isEqualTo("장원영");
      assertThat(participant.buncheolMemberId()).isEqualTo(101L);
      assertThat(participant.memberName()).isEqualTo("안유진");
      assertThat(participant.amount()).isEqualTo(53_000L);
      assertThat(participant.status()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(participant.dueAt()).isEqualTo(DUE_AT);
      assertThat(participant.confirmedAt()).isNull();
      assertThat(participant.refundAccount().bank()).isEqualTo("국민");
      assertThat(participant.refundAccount().account()).isEqualTo("12345678");
      assertThat(participant.refundAccount().holder()).isEqualTo("홍길동");
      assertThat(participant.delivery()).isNull();
    }

    @Test
    void 입금확인된_참여는_confirmedAt과_배송_스냅샷이_채워진다() {
      stubBasicBuncheol(BuncheolStatus.CONFIRMED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      Participation confirmed =
          participation(601L, 101L, PARTICIPANT_USER, 53_000L, ParticipationStatus.CONFIRMED);
      setField(confirmed, "confirmedAt", CONFIRMED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(confirmed));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      Delivery delivery =
          delivery(5001L, 601L, "GS25 강남역점", "유진팬", "010-1234-5678", "1234567890",
              DeliveryStatus.SHIPPING);
      given(deliveryRepository.findAllByParticipationIds(List.of(601L)))
          .willReturn(List.of(delivery));
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.confirmedCount()).isEqualTo(1);
      BuncheolManagementParticipantResponse participant = response.participants().get(0);
      assertThat(participant.status()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(participant.confirmedAt()).isEqualTo(CONFIRMED_AT);
      assertThat(participant.participantNickname()).isEqualTo("장원영");
      assertThat(participant.delivery()).isNotNull();
      assertThat(participant.delivery().deliveryId()).isEqualTo(5001L);
      assertThat(participant.delivery().shippingMethod()).isEqualTo(ShippingMethod.GS25_HALF);
      assertThat(participant.delivery().storeName()).isEqualTo("GS25 강남역점");
      assertThat(participant.delivery().receiverNickname()).isEqualTo("유진팬");
      assertThat(participant.delivery().receiverPhoneNumber()).isEqualTo("010-1234-5678");
      assertThat(participant.delivery().trackingNumber()).isEqualTo("1234567890");
      assertThat(participant.delivery().status()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    void 입금확인중과_확정_참여가_섞여있으면_confirmedCount는_확정만_센다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L), buncheolMember(102L, 1002L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L, 1002L)))
          .willReturn(List.of(groupMember(1001L, "안유진"), groupMember(1002L, "레이")));
      Participation awaiting =
          participation(601L, 101L, OTHER_USER, 53_000L, ParticipationStatus.AWAITING_PAYMENT);
      Participation confirmed =
          participation(602L, 102L, PARTICIPANT_USER, 43_000L, ParticipationStatus.CONFIRMED);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(awaiting, confirmed));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(deliveryRepository.findAllByParticipationIds(List.of(602L))).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(OTHER_USER, PARTICIPANT_USER)))
          .willReturn(List.of(user(OTHER_USER, "타인"), user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.memberCount()).isEqualTo(2);
      assertThat(response.confirmedCount()).isEqualTo(1);
      assertThat(response.participants()).hasSize(2);
    }

    @Test
    void 멤버_슬롯이_없는_분철도_정상_응답한다() {
      stubBasicBuncheol(BuncheolStatus.CANCELLED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of());
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(Collections.emptyList())).willReturn(List.of());

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.memberCount()).isZero();
      assertThat(response.participants()).isEmpty();
      // 멤버 슬롯이 없으면 GroupMember 조회는 일어나지 않는다.
      verify(groupMemberRepository, never()).findAllByGroupIdAndIds(any(), anyList());
    }

    // 취소된 참여는 활성 조회에서 빠져 개최자가 환불 계좌에 닿을 길이 없어진다. C2C 는 개최자가 환불 주체라 이 목록이 유일한 경로다.
    @Test
    void 취소된_참여는_환불계좌와_함께_별도_목록에_담긴다() {
      stubBasicBuncheol(BuncheolStatus.CANCELLED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      Participation cancelled =
          participation(601L, 101L, PARTICIPANT_USER, 53_000L, ParticipationStatus.CANCELLED);
      setField(cancelled, "confirmedAt", CONFIRMED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.cancelledParticipants()).hasSize(1);
      BuncheolManagementParticipantResponse refundTarget = response.cancelledParticipants().get(0);
      assertThat(refundTarget.participationId()).isEqualTo(601L);
      assertThat(refundTarget.participantNickname()).isEqualTo("장원영");
      assertThat(refundTarget.memberName()).isEqualTo("안유진");
      assertThat(refundTarget.amount()).isEqualTo(53_000L);
      assertThat(refundTarget.status()).isEqualTo(ParticipationStatus.CANCELLED);
      assertThat(refundTarget.confirmedAt()).isEqualTo(CONFIRMED_AT);
      assertThat(refundTarget.refundAccount().bank()).isEqualTo("국민");
      assertThat(refundTarget.refundAccount().account()).isEqualTo("12345678");
      assertThat(refundTarget.refundAccount().holder()).isEqualTo("홍길동");
      assertThat(refundTarget.delivery()).isNull();
    }

    @Test
    void 취소분은_참여자_목록과_확정_인원_집계에_섞이지_않는다() {
      stubBasicBuncheol(BuncheolStatus.CONFIRMED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      Participation active =
          participation(601L, 101L, PARTICIPANT_USER, 53_000L, ParticipationStatus.CONFIRMED);
      Participation cancelled =
          participation(602L, 101L, OTHER_USER, 53_000L, ParticipationStatus.CANCELLED);
      setField(cancelled, "confirmedAt", CONFIRMED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(active));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));
      given(deliveryRepository.findAllByParticipationIds(List.of(601L))).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER, OTHER_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영"), user(OTHER_USER, "안유진팬")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.participants()).hasSize(1);
      assertThat(response.confirmedCount()).isEqualTo(1);
      assertThat(response.cancelledParticipants()).hasSize(1);
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
    setField(buncheol, "minHeadcount", 3);
    setField(buncheol, "status", status);
    setField(buncheol, "shippingFeePolicy", ShippingFeePolicy.of(3000, null));
    return buncheol;
  }

  private BuncheolMember buncheolMember(final Long id, final Long memberId) {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", id);
    setField(member, "buncheolId", BUNCHEOL_ID);
    setField(member, "memberId", memberId);
    setField(member, "price", 50_000L);
    return member;
  }

  private Group group(final Long id, final String name) {
    Group group = newInstance(Group.class);
    setField(group, "id", id);
    setField(group, "name", name);
    return group;
  }

  private GroupMember groupMember(final Long id, final String name) {
    GroupMember member = newInstance(GroupMember.class);
    setField(member, "id", id);
    setField(member, "name", name);
    return member;
  }

  private User user(final Long id, final String nickname) {
    User user = newInstance(User.class);
    setField(user, "id", id);
    setField(user, "nickname", Nickname.of(nickname));
    return user;
  }

  private Participation participation(
      final Long id,
      final Long buncheolMemberId,
      final Long participantId,
      final long amount,
      final ParticipationStatus status) {
    Participation p = newInstance(Participation.class);
    setField(p, "id", id);
    setField(p, "buncheolId", BUNCHEOL_ID);
    setField(p, "buncheolMemberId", buncheolMemberId);
    setField(p, "participantId", participantId);
    setField(p, "shippingAddressId", 200L);
    setField(p, "amount", amount);
    setField(p, "refundAccount", REFUND_ACCOUNT);
    setField(p, "dueAt", DUE_AT);
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
