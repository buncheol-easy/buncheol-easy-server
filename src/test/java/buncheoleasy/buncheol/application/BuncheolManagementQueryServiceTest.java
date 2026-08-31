package buncheoleasy.buncheol.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
  @Mock private ParticipationBundleDomainService participationBundleDomainService;

  private static final Long BUNCHEOL_ID = 10L;
  private static final Long GROUP_ID = 100L;
  private static final Long HOST_ID = 777L;
  private static final Long OTHER_USER = 888L;
  private static final Long PARTICIPANT_USER = 555L;
  private static final Instant DEADLINE = Instant.parse("2026-06-01T12:00:00Z");
  private static final Instant DUE_AT = Instant.parse("2026-06-02T12:00:00Z");
  private static final Instant CONFIRMED_AT = Instant.parse("2026-06-01T18:00:00Z");
  private static final RefundAccount REFUND_ACCOUNT = RefundAccount.of("국민", "12345678", "홍길동");
  private static final Long BUNDLE_ID_BASE = 9000L;

  /**
   * 계좌·입금자명의 정본은 묶음이다 (P2-c). 픽스처가 심은 {@code bundleId} 를 가진 참여에는 계좌 있는 묶음을 돌려주고,
   * {@code bundleId} 가 없는 참여(배포선 창의 미연결 행)에는 아무것도 주지 않는다.
   */
  @BeforeEach
  void stubBundles() {
    lenient()
        .when(participationBundleDomainService.findAllByParticipations(any()))
        .thenAnswer(
            invocation -> {
              Collection<Participation> participations = invocation.getArgument(0);
              // 묶음 배송비 = 그 묶음 슬롯들에 저장된 배송비의 합 (묶음당 1회만 부과되므로 실제와 같다).
              // ⚠️ getId() 를 채워야 한다 — 비워 두면 배송비 귀속 판정이 조용히 꺼져 회귀를 놓친다.
              Map<Long, Long> feeByBundleId = new HashMap<>();
              for (Participation participation : participations) {
                if (participation.getBundleId() == null) {
                  continue;
                }
                feeByBundleId.merge(
                    participation.getBundleId(), participation.getShippingFee(), Long::sum);
              }
              Map<Long, ParticipationBundle> byId = new HashMap<>();
              feeByBundleId.forEach(
                  (bundleId, fee) -> {
                    ParticipationBundle bundle = mock(ParticipationBundle.class);
                    lenient().when(bundle.getId()).thenReturn(bundleId);
                    lenient().when(bundle.getShippingFee()).thenReturn(fee);
                    lenient().when(bundle.getRefundAccount()).thenReturn(REFUND_ACCOUNT);
                    byId.put(bundleId, bundle);
                  });
              return byId;
            });
  }

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
    void 입금확인중_참여는_입금자명과_dueAt이_노출되고_계좌와_배송은_null() {
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
      // 활성 참여는 통장 대조 키(입금자명)만 내리고 계좌번호는 감춘다 (docs/70 결정 21).
      assertThat(participant.depositorName()).isEqualTo("홍길동");
      assertThat(participant.refundAccount()).isNull();
      assertThat(participant.delivery()).isNull();
    }

    // 🔴 null 로 나가면 화면이 묶음 단위 경로 대신 슬롯 경로로 조용히 폴백한다 — 화면만 보면 티가 안 난다.
    // 배송비 테스트에 얹지 않고 떼어 둔다: 그 테스트가 리네임·삭제되면 이 커버리지가 같이 사라진다.
    @Test
    void 응답에_묶음_id와_참여자_id가_실린다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));

      final Long bundleId = 8888L;
      Participation active =
          participation(601L, 101L, PARTICIPANT_USER, 10_000L, ParticipationStatus.APPLIED);
      setField(active, "bundleId", bundleId);
      Participation cancelled =
          participation(602L, 101L, PARTICIPANT_USER, 10_000L, ParticipationStatus.CANCELLED);
      setField(cancelled, "bundleId", bundleId);

      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(active));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      BuncheolManagementParticipantResponse activeRow = response.participants().get(0);
      assertThat(activeRow.bundleId()).isEqualTo(bundleId);
      assertThat(activeRow.participantId()).isEqualTo(PARTICIPANT_USER);
      // 취소분도 같은 계약이다 — 개최자가 환불 대상을 묶음으로 접어 볼 때 필요하다.
      BuncheolManagementParticipantResponse deadRow = response.cancelledParticipants().get(0);
      assertThat(deadRow.bundleId()).isEqualTo(bundleId);
      assertThat(deadRow.participantId()).isEqualTo(PARTICIPANT_USER);
    }

    // 🔴 staging 재현 그대로 (분철 104 · 묶음 141 · 참여 232 취소 → 233).
    // 이 결함은 도메인 규칙이 아니라 <b>배선</b>에 있었다 — 쿼리 서비스가 묶음 슬롯을 빠짐없이 넘기는지가 정확성의 전부다.
    @Test
    void 배송비를_진_슬롯이_취소되면_남은_슬롯이_배송비를_이어받는다() {
      stubBasicBuncheol(BuncheolStatus.RECRUITING);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L), buncheolMember(102L, 1002L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L, 1002L)))
          .willReturn(List.of(groupMember(1001L, "안유진"), groupMember(1002L, "장원영")));

      final Long sharedBundleId = 9999L;
      // 232 — 배송비를 진 첫 슬롯. 취소됐다.
      Participation cancelled =
          participation(232L, 101L, PARTICIPANT_USER, 10_000L, ParticipationStatus.CANCELLED);
      setField(cancelled, "bundleId", sharedBundleId);
      setField(cancelled, "shippingFee", 3_000L);
      setField(cancelled, "cancelledAt", CONFIRMED_AT);
      // 233 — 같은 묶음의 남은 슬롯. 저장된 배송비는 0 이다.
      Participation remaining =
          participation(233L, 102L, PARTICIPANT_USER, 10_000L, ParticipationStatus.APPLIED);
      setField(remaining, "bundleId", sharedBundleId);

      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(remaining));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      // 남은 슬롯이 배송비를 진다 — 이걸 안 하면 개최자가 택배비 3,000 을 자기 돈으로 문다.
      BuncheolManagementParticipantResponse active = response.participants().get(0);
      assertThat(active.participationId()).isEqualTo(233L);
      assertThat(active.shippingFee()).isEqualTo(3_000L);
      assertThat(active.amount()).isEqualTo(13_000L);
      // 취소분은 배송비를 잃는다 — 택배가 계속 나가므로 그만큼은 환불 대상이 아니다.
      BuncheolManagementParticipantResponse dead = response.cancelledParticipants().get(0);
      assertThat(dead.participationId()).isEqualTo(232L);
      assertThat(dead.shippingFee()).isZero();
      assertThat(dead.amount()).isEqualTo(10_000L);
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

    // 이번 노출 축소의 핵심 분기. 이 테스트가 없으면 needsHostRefund 의 뒷 조건을 통째로 지워도
    // 전 테스트가 초록이라, 조건이 조용히 사라지는 것을 아무도 못 잡는다.
    @Test
    void 취소분이라도_입금_흔적이_없으면_계좌를_내리지_않는다() {
      stubBasicBuncheol(BuncheolStatus.CANCELLED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      // 마킹도 입금확인도 없이 취소된 참여 — 개최자가 돌려줄 돈이 애초에 없다.
      Participation cancelled =
          participation(601L, 101L, PARTICIPANT_USER, 53_000L, ParticipationStatus.CANCELLED);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      BuncheolManagementParticipantResponse target = response.cancelledParticipants().get(0);
      assertThat(target.refundAccount()).isNull();
      // 계좌는 감추되 통장 대조 키(입금자명)는 그대로 내려간다.
      assertThat(target.depositorName()).isEqualTo("홍길동");
    }

    // C2C 에서 흔한 경로: 마킹 → 개최자가 확인 못 함 → 기한 도과 취소. payment_sent_at 은 보존되므로
    // 개최자가 실제로 환불해야 하는 건이고, confirmedAt 이 없어도 계좌가 나와야 한다.
    @Test
    void 보냈어요_마킹만_있는_취소분도_계좌를_내린다() {
      stubBasicBuncheol(BuncheolStatus.CANCELLED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      Participation cancelled =
          participation(601L, 101L, PARTICIPANT_USER, 53_000L, ParticipationStatus.CANCELLED);
      setField(cancelled, "paymentSentAt", CONFIRMED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      BuncheolManagementParticipantResponse target = response.cancelledParticipants().get(0);
      assertThat(target.confirmedAt()).isNull();
      assertThat(target.refundAccount()).isNotNull();
      assertThat(target.refundAccount().holder()).isEqualTo("홍길동");
    }

    // 회귀 방지 — 2026-08-28 이전에 만들어진 0원 참여는 refund_* 가 NULL 이고, 이 화면이 그 값을 조건 없이
    // 역참조해 개최 관리 전체가 500 이었다(staging 참여 #226). 신규 참여는 계좌를 강제하지만(docs/80 결정 1)
    // 옛 행은 그대로 남으므로 이 케이스는 계속 200 이어야 한다.
    @Test
    void 계좌가_없는_0원_참여가_섞여도_500_없이_응답한다() {
      stubBasicBuncheol(BuncheolStatus.CONFIRMED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L), buncheolMember(102L, 1002L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L, 1002L)))
          .willReturn(List.of(groupMember(1001L, "안유진"), groupMember(1002L, "레이")));
      Participation free =
          participation(601L, 101L, PARTICIPANT_USER, 0L, ParticipationStatus.CONFIRMED);
      setField(free, "bundleId", null);
      setField(free, "confirmedAt", CONFIRMED_AT);
      Participation paid =
          participation(602L, 102L, OTHER_USER, 53_000L, ParticipationStatus.CONFIRMED);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(free, paid));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(deliveryRepository.findAllByParticipationIds(List.of(601L, 602L)))
          .willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER, OTHER_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영"), user(OTHER_USER, "안유진팬")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.participants()).hasSize(2);
      BuncheolManagementParticipantResponse target = response.participants().get(0);
      assertThat(target.participationId()).isEqualTo(601L);
      // 입금자명이 비면 클라(HostedBuncheolManage.tsx)가 닉네임으로 폴백한다.
      assertThat(target.depositorName()).isNull();
      assertThat(target.participantNickname()).isEqualTo("장원영");
      // 같은 목록의 유상 참여는 영향받지 않는다.
      assertThat(response.participants().get(1).depositorName()).isEqualTo("홍길동");
    }

    // 참여 계좌 강제(PR #151) 이후의 0원 참여는 계좌를 갖는다. 그래도 대조할 입금이 없어 예금주(실명)를
    // 내리지 않는다 — 이 필드의 존재 이유가 통장 대조이기 때문이다.
    @Test
    void 계좌가_있어도_0원_참여는_입금자명을_내리지_않는다() {
      stubBasicBuncheol(BuncheolStatus.CONFIRMED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      // 계좌는 채워져 있고(REFUND_ACCOUNT) 금액만 0원이다.
      Participation free =
          participation(601L, 101L, PARTICIPANT_USER, 0L, ParticipationStatus.CONFIRMED);
      setField(free, "confirmedAt", CONFIRMED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of(free));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(deliveryRepository.findAllByParticipationIds(List.of(601L))).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.participants().get(0).depositorName()).isNull();
    }

    // 0원 참여는 생성 즉시 CONFIRMED 라 취소되면 "환불 필요" 판정의 뒷 조건을 항상 만족한다. 계좌가 채워진
    // 뒤에도 금액 판정이 없으면 돌려줄 돈이 없는 건의 계좌번호가 개최자에게 내려간다.
    @Test
    void 계좌가_있어도_0원_참여가_취소되면_계좌를_내리지_않는다() {
      stubBasicBuncheol(BuncheolStatus.CANCELLED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      Participation cancelled =
          participation(601L, 101L, PARTICIPANT_USER, 0L, ParticipationStatus.CANCELLED);
      setField(cancelled, "confirmedAt", CONFIRMED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      BuncheolManagementParticipantResponse target = response.cancelledParticipants().get(0);
      assertThat(target.refundAccount()).isNull();
      assertThat(target.depositorName()).isNull();
    }

    // C2C 회귀 방지 — isFree() 는 슬롯 판정인데 C2C 통장 대조는 묶음(같은 사람) 단위다. 배송비가 첫 슬롯에만
    // 붙고 멤버 가격 0 도 허용돼 "0원 슬롯 + 유상 슬롯" 이 한 묶음이 될 수 있는데, 그 0원 행의 예금주를 지우면
    // 이체 1건에 대조 키가 갈린다. C2C 가 0원 슬롯에도 계좌를 요구하는 이유가 그것이다.
    @Test
    void C2C는_0원_슬롯이어도_입금자명을_내린다() {
      stubBasicBuncheol(BuncheolStatus.CONFIRMED, FlowType.C2C);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L), buncheolMember(102L, 1002L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L, 1002L)))
          .willReturn(List.of(groupMember(1001L, "안유진"), groupMember(1002L, "레이")));
      // 같은 사람의 다슬롯 — 배송비가 첫 슬롯에만 붙어 두 번째가 0원이 된다.
      Participation paid =
          participation(601L, 101L, PARTICIPANT_USER, 53_000L, ParticipationStatus.CONFIRMED);
      Participation freeSlot =
          participation(602L, 102L, PARTICIPANT_USER, 0L, ParticipationStatus.CONFIRMED);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(paid, freeSlot));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(deliveryRepository.findAllByParticipationIds(List.of(601L, 602L)))
          .willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      // 두 행의 대조 키가 갈리지 않아야 한다.
      assertThat(response.participants().get(0).depositorName()).isEqualTo("홍길동");
      assertThat(response.participants().get(1).depositorName()).isEqualTo("홍길동");
    }

    // FE 의 "환불이 필요한 참여" 목록 필터에는 금액 조건이 없다 (HostedBuncheolManage.tsx —
    // confirmedAt||paymentSentAt). C2C 0원 취소분의 계좌를 서버가 지우면 목록에는 뜨는데 계좌가 비는 행이 된다.
    @Test
    void C2C는_0원_취소분이어도_계좌를_내린다() {
      stubBasicBuncheol(BuncheolStatus.CANCELLED, FlowType.C2C);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      Participation cancelled =
          participation(601L, 101L, PARTICIPANT_USER, 0L, ParticipationStatus.CANCELLED);
      setField(cancelled, "confirmedAt", CONFIRMED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      BuncheolManagementParticipantResponse target = response.cancelledParticipants().get(0);
      assertThat(target.refundAccount()).isNotNull();
      assertThat(target.depositorName()).isEqualTo("홍길동");
    }

    // 계좌 강제 이후 "유상인데 계좌가 빈" 행은 계약상 존재할 수 없다. 그래도 null 가드를 남긴 이유가
    // 이것뿐이라, 가드를 지웠을 때 깨지는 테스트가 없으면 다음 사람이 조용히 지운다 (docs/80 결정 5).
    @Test
    void 계약이_깨져_유상_참여의_계좌가_비어도_500이_나지_않는다() {
      stubBasicBuncheol(BuncheolStatus.CONFIRMED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      Participation broken =
          participation(601L, 101L, PARTICIPANT_USER, 53_000L, ParticipationStatus.CONFIRMED);
      setField(broken, "bundleId", null);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(broken));
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of());
      given(deliveryRepository.findAllByParticipationIds(List.of(601L))).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      assertThat(response.participants()).hasSize(1);
      assertThat(response.participants().get(0).depositorName()).isNull();
    }

    // 0원 참여는 생성 즉시 CONFIRMED 라, 취소되면 "환불 필요" 판정에 걸려 계좌 응답 조립까지 간다.
    @Test
    void 계좌가_없는_0원_참여가_취소되면_계좌를_null로_내린다() {
      stubBasicBuncheol(BuncheolStatus.CANCELLED);
      given(buncheolMemberRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID))
          .willReturn(List.of(buncheolMember(101L, 1001L)));
      given(groupMemberRepository.findAllByGroupIdAndIds(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "안유진")));
      Participation cancelled =
          participation(601L, 101L, PARTICIPANT_USER, 0L, ParticipationStatus.CANCELLED);
      setField(cancelled, "bundleId", null);
      setField(cancelled, "confirmedAt", CONFIRMED_AT);
      given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID)).willReturn(List.of());
      given(participationRepository.findCancelledByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(cancelled));
      given(deliveryRepository.findAllByParticipationIds(List.of())).willReturn(List.of());
      given(userRepository.findAllByIds(List.of(PARTICIPANT_USER)))
          .willReturn(List.of(user(PARTICIPANT_USER, "장원영")));

      BuncheolManagementResponse response =
          buncheolManagementQueryService.getManagement(BUNCHEOL_ID, HOST_ID);

      BuncheolManagementParticipantResponse target = response.cancelledParticipants().get(0);
      assertThat(target.refundAccount()).isNull();
      assertThat(target.depositorName()).isNull();
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
    stubBasicBuncheol(status, FlowType.LEGACY);
  }

  private void stubBasicBuncheol(final BuncheolStatus status, final FlowType flowType) {
    Buncheol buncheol = buncheol(status);
    setField(buncheol, "flowType", flowType);
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
    setField(p, "bundleId", BUNDLE_ID_BASE + id);
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
