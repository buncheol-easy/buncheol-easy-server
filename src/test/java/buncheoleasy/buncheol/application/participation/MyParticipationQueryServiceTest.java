package buncheoleasy.buncheol.application.participation;

import org.junit.jupiter.api.BeforeEach;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationCancelReason;
import buncheoleasy.buncheol.domain.participation.ParticipationCancellability;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.dto.response.HostAccountResponse;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.user.domain.shipping.ShippingAddressRepository;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.buncheol.dto.response.RequestedShippingAddressResponse;
import buncheoleasy.buncheol.dto.response.ShippingOptionResponse;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackPolicy;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyParticipationQueryService 단위 테스트")
class MyParticipationQueryServiceTest {

  @InjectMocks private MyParticipationQueryService myParticipationQueryService;

  @Mock private ParticipationRepository participationRepository;
  @Mock private BuncheolRepository buncheolRepository;
  @Mock private BuncheolMemberRepository buncheolMemberRepository;
  @Mock private GroupMemberRepository groupMemberRepository;
  @Mock private BuncheolImageRepository buncheolImageRepository;
  @Mock private DeliveryRepository deliveryRepository;
  @Mock private UserRepository userRepository;
  @Mock private ShippingFeePaybackPolicy shippingFeePaybackPolicy;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private ShippingAddressRepository shippingAddressRepository;

  // Instant.now(clock) 가 실제 시각을 돌려주도록 고정 Clock 을 @Spy 로 주입한다 (mock Clock 은 NPE).
  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

  private static final Long PARTICIPANT_ID = 100L;
  private static final Long HOST_ID = 900L;
  private static final RefundAccount REFUND_ACCOUNT = RefundAccount.of("국민", "12345678", "홍길동");
  private static final Instant DUE_AT = Instant.parse("2026-05-14T12:30:00Z");


  /** 계좌의 정본은 묶음이다 (P2-c) — bundleId 가 있는 참여에는 계좌 있는 묶음을 돌려준다. */
  @BeforeEach
  void stubBundles() {
    lenient()
        .when(participationBundleDomainService.findAllByParticipations(any()))
        .thenAnswer(
            invocation -> {
              java.util.Collection<Participation> participations = invocation.getArgument(0);
              // 묶음 배송비 = 그 묶음 슬롯들에 저장된 배송비의 합 (묶음당 1회만 부과되므로 실제와 같다).
              // ⚠️ getId() 를 채워야 한다 — 비워 두면 배송비 귀속 판정이 조용히 꺼져 회귀를 놓친다.
              java.util.Map<Long, Long> feeByBundleId = new java.util.HashMap<>();
              for (Participation participation : participations) {
                if (participation.getBundleId() == null) {
                  continue;
                }
                feeByBundleId.merge(
                    participation.getBundleId(), participation.getShippingFee(), Long::sum);
              }
              java.util.Map<Long, ParticipationBundle> byId = new java.util.HashMap<>();
              feeByBundleId.forEach(
                  (bundleId, fee) -> {
                    ParticipationBundle bundle = mock(ParticipationBundle.class);
                    lenient().when(bundle.getId()).thenReturn(bundleId);
                    lenient().when(bundle.getShippingFee()).thenReturn(fee);
                    lenient()
                        .when(bundle.getRefundAccount())
                        .thenReturn(RefundAccount.of("국민", "12345678", "홍길동"));
                    byId.put(bundleId, bundle);
                  });
              return byId;
            });
  }

  /** 배송지를 심은 묶음 목. 사본과 <b>다른</b> 값을 줘야 이관 여부를 테스트가 말해 준다. */
  private static ParticipationBundle bundleWithAddress(final Long bundleId, final Long addressId) {
    ParticipationBundle bundle = mock(ParticipationBundle.class);
    lenient().when(bundle.getId()).thenReturn(bundleId);
    lenient().when(bundle.getShippingAddressId()).thenReturn(addressId);
    lenient().when(bundle.getShippingFee()).thenReturn(0L);
    lenient().when(bundle.getRefundAccount()).thenReturn(REFUND_ACCOUNT);
    return bundle;
  }

  @Nested
  @DisplayName("내 참여 목록 조회 테스트")
  class GetMyParticipationsTest {

    // 🔴 배송지 정본은 <b>묶음</b>이다. 화면의 「배송지 고정 · 변경 불가」 자리에 이 값이 들어가므로,
    // 서버가 안 내려주면 화면이 유저의 기본 배송지로 폴백해 <b>틀린 주소를 확신에 차서</b> 보여 준다.
    // 참여 사본에 다른 값(999)을 심어 둔다 — 같은 값이면 사본을 읽어도 초록이라 이관을 검증 못 한다.
    @Test
    void 배송지는_참여_사본이_아니라_묶음_정본에서_읽는다() {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      Buncheol buncheol = buncheol(10L, "C2C 분철", deadline, BuncheolStatus.RECRUITING);
      Participation participation =
          participation(
              233L, 10L, 101L, 10_000L, ParticipationStatus.AWAITING_PAYMENT, DUE_AT, null, null);
      setField(participation, "bundleId", 9999L);
      setField(participation, "shippingAddressId", 999L); // 사본 — 읽히면 안 된다

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(participation));
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
          .willReturn(List.of(buncheolMember(101L, 10L, 1001L)));
      given(groupMemberRepository.findAllByIds(List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "해린")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L)))
          .willReturn(List.of());
      given(deliveryRepository.findAllByBundleIds(List.of())).willReturn(List.of());
      // 묶음 정본이 가리키는 배송지는 200 이다. 배치 조회 결과에서 꺼내므로 묶음에 심는다.
      // ⚠️ 헬퍼가 내부에서 스터빙하므로 given(...) 안에서 부르면 UnfinishedStubbing 이 난다 — 먼저 만든다.
      ParticipationBundle bundle = bundleWithAddress(9999L, 200L);
      given(participationBundleDomainService.findAllByParticipations(List.of(participation)))
          .willReturn(Map.of(9999L, bundle));
      given(shippingAddressRepository.findAllByIds(List.of(200L)))
          .willReturn(
              List.of(
                  new ShippingAddress(
                      200L, PARTICIPANT_ID, ShippingMethod.GS25_HALF, "GS25 강남역점", null, true)));

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).requestedShippingAddress())
          .isEqualTo(new RequestedShippingAddressResponse("GS25_HALF", "GS25 강남역점"));
      // 사본(999)을 조회하지 않았다 — 조회했다면 이관이 안 된 것이다.
      then(shippingAddressRepository).should(never()).findAllByIds(List.of(999L));
    }

    // 🔴 <b>기본 배송지로 폴백하지 않는다.</b> 「변경 불가」 라벨이 붙는 자리라, 실제로 가지 않을 주소를
    // 확신에 차서 보여 주는 것이 "정보 없음" 보다 나쁘다. null 을 내려 화면이 빈 상태로 그리게 한다.
    @Test
    void 묶음_배송지를_못_읽으면_대체값_없이_null_을_내린다() {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      Buncheol buncheol = buncheol(10L, "C2C 분철", deadline, BuncheolStatus.RECRUITING);
      Participation participation =
          participation(
              233L, 10L, 101L, 10_000L, ParticipationStatus.AWAITING_PAYMENT, DUE_AT, null, null);
      setField(participation, "bundleId", 9999L);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(participation));
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
          .willReturn(List.of(buncheolMember(101L, 10L, 1001L)));
      given(groupMemberRepository.findAllByIds(List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "해린")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L)))
          .willReturn(List.of());
      given(deliveryRepository.findAllByBundleIds(List.of())).willReturn(List.of());
      // 묶음은 있는데 배송지가 비었다(참조하던 주소가 지워진 경우). 사본도 비어 있다.
      ParticipationBundle emptyAddressBundle = bundleWithAddress(9999L, null);
      given(participationBundleDomainService.findAllByParticipations(List.of(participation)))
          .willReturn(Map.of(9999L, emptyAddressBundle));

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result.get(0).requestedShippingAddress()).isNull();
      // 배송지 조회 자체를 하지 않는다 — 빈 목록으로 부르면 IS NULL 조회가 되어 남의 주소가 걸린다.
      then(shippingAddressRepository).shouldHaveNoInteractions();
    }

    @Test
    void 참여_내역이_없으면_빈_리스트를_반환한다() {
      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of());

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).isEmpty();
    }

    // 🔴 staging 재현 그대로 (232 취소 → 233). 참여자가 <b>직접 보고 이체하는 금액</b>이라
    // 개최 관리보다 더 강하게 회귀를 막아야 한다.
    @Test
    void 배송비를_진_슬롯이_취소되면_남은_슬롯이_배송비를_이어받는다() {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      Buncheol buncheol = buncheol(10L, "C2C 다슬롯 분철", deadline, BuncheolStatus.RECRUITING);
      List<BuncheolMember> slots =
          List.of(buncheolMember(101L, 10L, 1001L), buncheolMember(102L, 10L, 1002L));
      final Long sharedBundleId = 9999L;

      // 232 — 배송비를 지고 있던 첫 슬롯. 취소됐다.
      Participation cancelled =
          participation(
              232L, 10L, 101L, 10_000L, ParticipationStatus.CANCELLED, DUE_AT, null, null);
      setField(cancelled, "shippingFee", 3_000L);
      setField(cancelled, "bundleId", sharedBundleId);
      setField(cancelled, "cancelledAt", DUE_AT);
      // 233 — 같은 묶음의 남은 슬롯. 저장된 배송비는 0 이다.
      Participation remaining =
          participation(
              233L, 10L, 102L, 10_000L, ParticipationStatus.APPLIED, DUE_AT, null, null);
      setField(remaining, "bundleId", sharedBundleId);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(remaining, cancelled));
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L))).willReturn(slots);
      given(groupMemberRepository.findAllByIds(List.of(1001L, 1002L)))
          .willReturn(List.of(groupMember(1001L, "해린"), groupMember(1002L, "민지")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L)))
          .willReturn(List.of());
      // 둘 다 입금확인 전이라 배송 조회 대상이 없다.
      given(deliveryRepository.findAllByBundleIds(List.of())).willReturn(List.of());

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      MyParticipationResponse active =
          result.stream().filter(r -> r.participationId().equals(233L)).findFirst().orElseThrow();
      // 남은 슬롯이 배송비를 진다 — 이걸 안 하면 참여자가 10,000 만 보내고 개최자가 택배비를 문다.
      assertThat(active.shippingFee()).isEqualTo(3_000L);
      assertThat(active.amount()).isEqualTo(13_000L);

      MyParticipationResponse dead =
          result.stream().filter(r -> r.participationId().equals(232L)).findFirst().orElseThrow();
      assertThat(dead.shippingFee()).isZero();
      assertThat(dead.amount()).isEqualTo(10_000L);
      // 묶음 단위 API(「보냈어요」 마킹)의 주소다. 활성분·취소분 모두 같은 계약이다.
      assertThat(active.bundleId()).isEqualTo(sharedBundleId);
      assertThat(dead.bundleId()).isEqualTo(sharedBundleId);
    }

    @Test
    void 참여_정보_분철_정보_멤버_이름_슬롯_수를_조합해_반환한다() {
      // 분철 1: id=10, title="뉴진스 1집 분철", deadline=+7일, status=RECRUITING, 슬롯 5개
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      Buncheol buncheol = buncheol(10L, "뉴진스 1집 분철", deadline, BuncheolStatus.RECRUITING);
      List<BuncheolMember> slots =
          List.of(
              buncheolMember(101L, 10L, 1001L),
              buncheolMember(102L, 10L, 1002L),
              buncheolMember(103L, 10L, 1003L),
              buncheolMember(104L, 10L, 1004L),
              buncheolMember(105L, 10L, 1005L));
      // 내가 참여한 슬롯: 102 (멤버 group_members.id=1002 → 이름 "민지")
      Participation participation =
          participation(
              500L, 10L, 102L, 50_000L, ParticipationStatus.AWAITING_PAYMENT, DUE_AT, null, null);
      // 멤버 가격 50_000 + 배송비 3_000 → 응답 amount(총액)는 53_000, 배송비는 shippingFee 로 분리 노출된다.
      setField(participation, "shippingFee", 3_000L);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(participation));
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L))).willReturn(slots);
      // 참여한 슬롯(102) 의 멤버(1002) 만 조회된다.
      given(groupMemberRepository.findAllByIds(List.of(1002L)))
          .willReturn(List.of(groupMember(1002L, "민지")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L)))
          .willReturn(List.of(BuncheolImage.create(10L, "https://cdn.example.com/10-thumb.jpg", false)));
      given(deliveryRepository.findAllByBundleIds(List.of())).willReturn(List.of());
      // 입금확인중 참여가 있으므로 개최자 계좌를 배치 조회한다.
      given(userRepository.findAllByIds(List.of(HOST_ID)))
          .willReturn(List.of(host(HOST_ID, "국민", "98765432", "개최자")));

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).hasSize(1);
      MyParticipationResponse response = result.get(0);
      assertThat(response.participationId()).isEqualTo(500L);
      assertThat(response.buncheolId()).isEqualTo(10L);
      assertThat(response.buncheolTitle()).isEqualTo("뉴진스 1집 분철");
      assertThat(response.buncheolMemberCount()).isEqualTo(5);
      assertThat(response.memberName()).isEqualTo("민지");
      assertThat(response.amount()).isEqualTo(53_000L);
      assertThat(response.shippingFee()).isEqualTo(3_000L);
      assertThat(response.participationStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(response.cancelReason()).isNull();
      assertThat(response.buncheolStatus()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(response.buncheolDeadline()).isEqualTo(deadline);
      assertThat(response.dueAt()).isEqualTo(DUE_AT);
      assertThat(response.confirmedAt()).isNull();
      assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.example.com/10-thumb.jpg");
      assertThat(response.shippingOptions())
          .containsExactly(new ShippingOptionResponse(ShippingMethod.GS25_HALF, 1_800));
      // 입금확인중이므로 개최자 계좌가 노출된다.
      assertThat(response.hostAccount())
          .isEqualTo(new HostAccountResponse("국민", "98765432", "개최자"));
      // 배송 스냅샷은 입금확인 시 생성되므로 아직 null.
      assertThat(response.delivery()).isNull();
    }

    @Test
    void 개최자_계좌가_미등록이면_입금확인중이어도_hostAccount_없이_반환한다() {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      Buncheol buncheol = buncheol(10L, "뉴진스 1집 분철", deadline, BuncheolStatus.RECRUITING);
      Participation participation =
          participation(
              500L, 10L, 101L, 53_000L, ParticipationStatus.AWAITING_PAYMENT, DUE_AT, null, null);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(participation));
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
          .willReturn(List.of(buncheolMember(101L, 10L, 1001L)));
      given(groupMemberRepository.findAllByIds(List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());
      given(deliveryRepository.findAllByBundleIds(List.of())).willReturn(List.of());
      // 계좌 미등록 개최자.
      given(userRepository.findAllByIds(List.of(HOST_ID)))
          .willReturn(List.of(hostWithoutBankAccount(HOST_ID)));

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).hostAccount()).isNull();
    }

    @Test
    void 개최자_취소로_자동_CANCELLED_된_참여도_사유와_함께_응답에_포함된다() {
      // 호스트가 분철을 취소하면 활성 참여는 BuncheolService 흐름에서 자동 CANCELLED(BUNCHEOL_CANCELLED) 로 전이된다.
      // 분철은 CANCELLED, 참여도 CANCELLED 상태이며, 사용자에겐 "취소된 참여 이력" 으로 그대로 보여야 한다.
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      Buncheol cancelled = buncheol(20L, "개최자가 취소한 분철", deadline, BuncheolStatus.CANCELLED);
      Participation autoCancelled =
          participation(
              600L,
              20L,
              201L,
              33_000L,
              ParticipationStatus.CANCELLED,
              DUE_AT,
              null,
              ParticipationCancelReason.BUNCHEOL_CANCELLED);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(autoCancelled));
      given(buncheolRepository.findAllByIds(List.of(20L))).willReturn(List.of(cancelled));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(20L)))
          .willReturn(List.of(buncheolMember(201L, 20L, 2001L)));
      given(groupMemberRepository.findAllByIds(List.of(2001L)))
          .willReturn(List.of(groupMember(2001L, "지수")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(20L))).willReturn(List.of());
      given(deliveryRepository.findAllByBundleIds(List.of())).willReturn(List.of());

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).buncheolId()).isEqualTo(20L);
      assertThat(result.get(0).participationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
      assertThat(result.get(0).cancelReason())
          .isEqualTo(ParticipationCancelReason.BUNCHEOL_CANCELLED);
      assertThat(result.get(0).buncheolStatus()).isEqualTo(BuncheolStatus.CANCELLED);
      assertThat(result.get(0).amount()).isEqualTo(33_000L);
      // 입금확인중이 아니므로 개최자 계좌를 노출하지 않는다.
      assertThat(result.get(0).hostAccount()).isNull();
      assertThat(result.get(0).thumbnailUrl()).isNull();
    }

    @Test
    void 여러_분철에_참여한_경우_분철별로_필드를_매핑한다() {
      Instant deadlineA = Instant.now().plus(3, ChronoUnit.DAYS);
      Instant deadlineB = Instant.now().plus(5, ChronoUnit.DAYS);
      Buncheol buncheolA = buncheol(10L, "분철 A", deadlineA, BuncheolStatus.CONFIRMED);
      Buncheol buncheolB = buncheol(20L, "분철 B", deadlineB, BuncheolStatus.RECRUITING);

      // A: 슬롯 2개 (참여한 슬롯 = 201, 멤버 이름 "지수")
      // B: 슬롯 4개 (참여한 슬롯 = 301, 멤버 이름 "제니")
      List<BuncheolMember> slots =
          List.of(
              buncheolMember(201L, 10L, 2001L),
              buncheolMember(202L, 10L, 2002L),
              buncheolMember(301L, 20L, 3001L),
              buncheolMember(302L, 20L, 3002L),
              buncheolMember(303L, 20L, 3003L),
              buncheolMember(304L, 20L, 3004L));

      Instant confirmedAt = Instant.now();
      Participation pA =
          participation(
              500L, 10L, 201L, 83_000L, ParticipationStatus.CONFIRMED, DUE_AT, confirmedAt, null);
      setField(pA, "bundleId", 9500L);
      Participation pB =
          participation(
              600L, 20L, 301L, 33_000L, ParticipationStatus.AWAITING_PAYMENT, DUE_AT, null, null);
      setField(pB, "bundleId", 9600L);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(pA, pB));
      given(buncheolRepository.findAllByIds(List.of(10L, 20L)))
          .willReturn(List.of(buncheolA, buncheolB));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L, 20L))).willReturn(slots);
      // 참여한 슬롯(201, 301) 의 멤버(2001, 3001) 만 조회된다.
      given(groupMemberRepository.findAllByIds(List.of(2001L, 3001L)))
          .willReturn(List.of(groupMember(2001L, "지수"), groupMember(3001L, "제니")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 20L)))
          .willReturn(List.of(BuncheolImage.create(10L, "https://cdn.example.com/10-thumb.jpg", false)));
      // 입금확인된 슬롯의 묶음만 조회한다 — pB 는 AWAITING_PAYMENT 라 애초에 배송이 없다.
      given(deliveryRepository.findAllByBundleIds(List.of(9500L)))
          .willReturn(List.of(delivery(900L, 500L, 9500L, "1234567890", DeliveryStatus.SHIPPING)));
      given(userRepository.findAllByIds(List.of(HOST_ID)))
          .willReturn(List.of(host(HOST_ID, "국민", "98765432", "개최자")));

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).hasSize(2);

      MyParticipationResponse first = result.get(0);
      assertThat(first.buncheolTitle()).isEqualTo("분철 A");
      assertThat(first.buncheolMemberCount()).isEqualTo(2);
      assertThat(first.memberName()).isEqualTo("지수");
      assertThat(first.amount()).isEqualTo(83_000L);
      assertThat(first.participationStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(first.buncheolStatus()).isEqualTo(BuncheolStatus.CONFIRMED);
      assertThat(first.confirmedAt()).isEqualTo(confirmedAt);
      assertThat(first.delivery().deliveryId()).isEqualTo(900L);
      assertThat(first.delivery().trackingNumber()).isEqualTo("1234567890");
      assertThat(first.delivery().status()).isEqualTo(DeliveryStatus.SHIPPING);
      assertThat(first.hostAccount()).isNull();

      MyParticipationResponse second = result.get(1);
      assertThat(second.buncheolTitle()).isEqualTo("분철 B");
      assertThat(second.buncheolMemberCount()).isEqualTo(4);
      assertThat(second.memberName()).isEqualTo("제니");
      assertThat(second.amount()).isEqualTo(33_000L);
      assertThat(second.participationStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(second.buncheolStatus()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(second.confirmedAt()).isNull();
      assertThat(second.hostAccount())
          .isEqualTo(new HostAccountResponse("국민", "98765432", "개최자"));
      assertThat(second.delivery()).isNull();
    }
  }

  @Nested
  @DisplayName("취소 가능 여부 노출 테스트 (docs/56 S-1)")
  class CancellabilityTest {

    private static final Instant FINALIZED_AT = Instant.parse("2026-05-14T10:00:00Z");

    /**
     * docs/46 §4.7-E1 — 입금 수집중 분철의 추가 모집 참여는 APPLIED 를 거치지 않고 곧바로 AWAITING_PAYMENT 로 생성된다.
     * 상태만 보고 판정하면 이 참여자는 신청 즉시 24시간 잠기고 화면의 취소 버튼도 사라진다. 반드시 CANCELLABLE 이어야 한다.
     */
    @Test
    void 추가_모집으로_입금_대기가_된_참여는_취소_가능으로_내려간다() {
      List<MyParticipationResponse> result =
          응답(
              ParticipationStatus.AWAITING_PAYMENT,
              FINALIZED_AT.plus(1, ChronoUnit.HOURS),
              FINALIZED_AT);

      assertThat(result.get(0).cancellability())
          .isEqualTo(ParticipationCancellability.CANCELLABLE);
    }

    @Test
    void 성사_확정을_거친_입금_대기_참여는_확정_사유와_함께_취소_불가로_내려간다() {
      List<MyParticipationResponse> result =
          응답(
              ParticipationStatus.AWAITING_PAYMENT,
              FINALIZED_AT.minus(1, ChronoUnit.HOURS),
              FINALIZED_AT);

      assertThat(result.get(0).cancellability())
          .isEqualTo(ParticipationCancellability.BLOCKED_BY_HOST_CONFIRM);
    }

    @Test
    void 보냈어요_참여는_상태_사유와_함께_취소_불가로_내려간다() {
      List<MyParticipationResponse> result =
          응답(
              ParticipationStatus.PAYMENT_SENT,
              FINALIZED_AT.minus(1, ChronoUnit.HOURS),
              FINALIZED_AT);

      assertThat(result.get(0).cancellability())
          .isEqualTo(ParticipationCancellability.BLOCKED_BY_STATUS);
    }

    private List<MyParticipationResponse> 응답(
        final ParticipationStatus status, final Instant createdAt, final Instant finalizedAt) {
      Buncheol c2c =
          buncheol(10L, "C2C 분철", Instant.now().plus(7, ChronoUnit.DAYS), BuncheolStatus.PAYMENT_COLLECTING);
      setField(c2c, "flowType", FlowType.C2C);
      setField(c2c, "finalizedAt", finalizedAt);
      Participation participation =
          participation(500L, 10L, 101L, 50_000L, status, DUE_AT, null, null);
      setField(participation, "createdAt", createdAt);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(participation));
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(c2c));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
          .willReturn(List.of(buncheolMember(101L, 10L, 1001L)));
      given(groupMemberRepository.findAllByIds(List.of(1001L)))
          .willReturn(List.of(groupMember(1001L, "민지")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());
      given(deliveryRepository.findAllByBundleIds(List.of())).willReturn(List.of());

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);
      assertThat(result).hasSize(1);
      return result;
    }
  }

  private Buncheol buncheol(Long id, String title, Instant deadline, BuncheolStatus status) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "hostId", HOST_ID);
    setField(buncheol, "title", title);
    setField(buncheol, "deadline", deadline);
    setField(buncheol, "status", status);
    setField(buncheol, "shippingFeePolicy", ShippingFeePolicy.of(1_800, null));
    return buncheol;
  }

  private User host(Long id, String bank, String account, String holder) {
    User user = newInstance(User.class);
    setField(user, "id", id);
    user.updateBankAccount(bank, account, holder);
    return user;
  }

  private User hostWithoutBankAccount(Long id) {
    User user = newInstance(User.class);
    setField(user, "id", id);
    return user;
  }

  private Delivery delivery(
      Long id, Long participationId, Long bundleId, String trackingNumber, DeliveryStatus status) {
    Delivery delivery =
        Delivery.createSnapshot(
            participationId, bundleId, ShippingMethod.GS25_HALF, "GS25 강남점", "수령인", "01012345678");
    setField(delivery, "id", id);
    setField(delivery, "trackingNumber", trackingNumber);
    setField(delivery, "status", status);
    return delivery;
  }

  private BuncheolMember buncheolMember(Long id, Long buncheolId, Long memberId) {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", id);
    setField(member, "buncheolId", buncheolId);
    setField(member, "memberId", memberId);
    return member;
  }

  private GroupMember groupMember(Long id, String name) {
    return new GroupMember(id, 1L, name, null);
  }

  private Participation participation(
      Long id,
      Long buncheolId,
      Long buncheolMemberId,
      long amount,
      ParticipationStatus status,
      Instant dueAt,
      Instant confirmedAt,
      ParticipationCancelReason cancelReason) {
    Participation participation =
        Participation.create(
            buncheolId, buncheolMemberId, PARTICIPANT_ID, 1L, amount, 0L, dueAt);
    setField(participation, "id", id);
    setField(participation, "status", status);
    setField(participation, "confirmedAt", confirmedAt);
    setField(participation, "cancelReason", cancelReason);
    return participation;
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
