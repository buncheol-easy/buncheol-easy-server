package buncheoleasy.buncheol.application.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import buncheoleasy.buncheol.application.BuncheolConfirmedFinalizer;
import buncheoleasy.buncheol.application.PaymentStarter;
import buncheoleasy.buncheol.application.DeliverySnapshotCreator;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.domain.code.ParticipationCode;
import buncheoleasy.buncheol.domain.code.ParticipationCodeDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.domain.staff.StaffBuncheolSetting;
import buncheoleasy.buncheol.domain.staff.StaffBuncheolSettingDomainService;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationService 단위 테스트")
class ParticipationServiceTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Long BUNCHEOL_MEMBER_ID = 101L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long HOST_ID = 1L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;
  private static final Long PARTICIPATION_ID = 500L;
  private static final long MEMBER_PRICE = 50_000L;
  private static final long SHIPPING_FEE = 3_000L;
  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");
  private static final BankAccount HOST_ACCOUNT = BankAccount.of("국민", "98765432", "개최자");
  private static final BankAccount PARTICIPANT_ACCOUNT = BankAccount.of("국민", "12345678", "홍길동");
  private static final RefundAccount REFUND_ACCOUNT_SNAPSHOT =
      RefundAccount.of("국민", "12345678", "홍길동");

  @InjectMocks private ParticipationService participationService;

  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private ParticipationCodeDomainService participationCodeDomainService;
  @Mock private StaffBuncheolSettingDomainService staffBuncheolSettingDomainService;
  @Mock private ParticipationShippingAddressResolver participationShippingAddressResolver;
  @Mock private UserDomainService userDomainService;
  @Mock private DeliverySnapshotCreator deliverySnapshotCreator;
  @Mock private BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  @Mock private PaymentStarter paymentStarter;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Captor private ArgumentCaptor<Participation> participationCaptor;

  private static final Long SECOND_MEMBER_ID = 202L;
  private static final long SECOND_MEMBER_PRICE = 21_000L;

  private ParticipateRequest participateRequest() {
    return new ParticipateRequest(BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID);
  }

  /** 환불 계좌는 요청이 아니라 참여자의 마이페이지 정산 계좌에서 읽는다. */
  private void givenParticipantAccount() {
    User participant = mock(User.class);
    given(participant.getBankAccount()).willReturn(PARTICIPANT_ACCOUNT);
    given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(participant);
  }

  private void givenOpenSlot() {
    given(participationCodeDomainService.validateForParticipation(any(), any(), any()))
        .willReturn(Optional.empty());
  }

  private ShippingAddress shippingAddress() {
    return new ShippingAddress(
        SHIPPING_ADDRESS_ID, PARTICIPANT_ID, ShippingMethod.GS25_HALF, "GS25 강남역점", null, false);
  }

  private BuncheolMember buncheolMember() {
    return buncheolMember(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);
  }

  private BuncheolMember buncheolMember(final Long id, final long price) {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", id);
    setField(member, "buncheolId", BUNCHEOL_ID);
    setField(member, "memberId", 1001L);
    setField(member, "price", price);
    return member;
  }

  @Nested
  @DisplayName("참여 신청 테스트")
  class ParticipateTest {

    @Test
    void 참여에_성공하면_금액과_입금만료시각과_개최자_계좌를_반환하고_요청_이벤트를_발행한다() {
      Instant deadline = NOW.plus(Duration.ofDays(7));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(deadline);
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      givenOpenSlot();
      givenParticipantAccount();
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                // 저장 성공 시 영속화로 id 가 채워지는 것을 흉내낸다.
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      // 10분 창이 deadline 보다 빠르므로 dueAt = now + 10분.
      Instant expectedDueAt = NOW.plus(Duration.ofMinutes(10));
      assertThat(result.totalAmount()).isEqualTo(MEMBER_PRICE + SHIPPING_FEE);
      assertThat(result.dueAt()).isEqualTo(expectedDueAt);
      assertThat(result.hostAccount()).isEqualTo(HOST_ACCOUNT);

      // 가드 호출이 실수로 제거되는 회귀를 잡는다.
      then(userDomainService).should().requireProfileCompleted(PARTICIPANT_ID);
      then(buncheol).should().validateRecruiting(NOW);
      then(participationDomainService)
          .should()
          .createParticipationIfRecruiting(participationCaptor.capture());
      Participation saved = participationCaptor.getValue();
      assertThat(saved.getBuncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(saved.getBuncheolMemberId()).isEqualTo(BUNCHEOL_MEMBER_ID);
      assertThat(saved.getParticipantId()).isEqualTo(PARTICIPANT_ID);
      assertThat(saved.getShippingAddressId()).isEqualTo(SHIPPING_ADDRESS_ID);
      // 멤버 금액과 배송비는 분리 저장되고, 총액(getTotalAmount)에만 합산된다.
      assertThat(saved.getAmount()).isEqualTo(MEMBER_PRICE);
      assertThat(saved.getShippingFee()).isEqualTo(SHIPPING_FEE);
      assertThat(saved.getAmount() + saved.getShippingFee())
          .isEqualTo(MEMBER_PRICE + SHIPPING_FEE);
      assertThat(saved.getDueAt()).isEqualTo(expectedDueAt);
      // 계좌는 참여가 아니라 묶음이 갖는다 (P2-c) — 참여 행에는 더 이상 실리지 않는다.
      then(participationBundleDomainService)
          .should()
          .attach(
              any(),
              isNull(),
              eq(SHIPPING_ADDRESS_ID),
              eq(SHIPPING_FEE),
              eq(
                  RefundAccount.of(
                      PARTICIPANT_ACCOUNT.bank(),
                      PARTICIPANT_ACCOUNT.account(),
                      PARTICIPANT_ACCOUNT.holder())),
              eq(expectedDueAt),
              eq(NOW));
      then(eventPublisher).should().publishEvent(any(ParticipationCreatedEvent.class));
    }

    @Test
    void 정산_계좌가_없으면_유상_참여를_거부한다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(NOW.plus(Duration.ofDays(7)));
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      givenOpenSlot();
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(mock(User.class));

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    @Test
    void 최소_자릿수_규칙_도입_전에_등록된_계좌로도_참여할_수_있다() {
      // 등록 시점 규칙(validateForRegistration)을 참여 경로에서 다시 걸면 기존 계좌 보유자의 참여가 막힌다.
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(NOW.plus(Duration.ofDays(7)));
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      givenOpenSlot();
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });
      User participant = mock(User.class);
      given(participant.getBankAccount()).willReturn(BankAccount.of("국민", "111", "홍길동"));
      given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(participant);
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(participationDomainService).should().createParticipationIfRecruiting(any());
      then(participationBundleDomainService)
          .should()
          .attach(
              any(),
              isNull(),
              any(),
              eq(SHIPPING_FEE),
              eq(RefundAccount.of("국민", "111", "홍길동")),
              any(),
              eq(NOW));
    }

    @Test
    void 같은_분철에_이미_활성_참여가_있으면_저장하지_않고_예외가_발생한다() {
      // 오픈 이벤트 운영 정책: 분철당 참여 1건(멤버 1명). 취소·만료된 참여는 재참여를 막지 않는다(mock 기본 false).
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(
              participationDomainService.hasActiveParticipationInBuncheol(
                  BUNCHEOL_ID, PARTICIPANT_ID))
          .willReturn(true);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_ALREADY_JOINED_BUNCHEOL);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    // 🔴 LEGACY 는 1인 1활성슬롯이 DB 유니크로 강제돼 있다. 그냥 흘려보내면 2번째 INSERT 가 유니크에 막혀
    // "이미 참여했다" 로 보이고, 사용자는 왜 여러 개를 못 잡는지 알 수 없다. 요청 단계에서 사유를 드러낸다.
    @Test
    void LEGACY_분철에는_다중_슬롯_신청을_열지_않는다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isC2c()).willReturn(false);

      ParticipateRequest multi =
          new ParticipateRequest(null, List.of(101L, 102L), SHIPPING_ADDRESS_ID, null, null);

      assertThatThrownBy(() -> participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, multi))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    // 입금 단계가 시작된 분철은 신청 자체를 받지 않는다 — 다중 슬롯도 마찬가지다.
    @Test
    void 입금_수집_중에는_다중_슬롯_신청도_받지_않는다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.PAYMENT_COLLECTING);

      ParticipateRequest multi =
          new ParticipateRequest(null, List.of(101L, 102L), SHIPPING_ADDRESS_ID, null, null);

      assertThatThrownBy(() -> participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, multi))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    // 코드는 슬롯 하나에 대응한다 — 여러 슬롯에 같은 코드를 재사용할 수 없다.
    @Test
    void 다중_슬롯에는_참여_코드를_쓸_수_없다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.RECRUITING);

      ParticipateRequest multi =
          new ParticipateRequest(null, List.of(101L, 102L), SHIPPING_ADDRESS_ID, null, "ABCD2345");

      assertThatThrownBy(() -> participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, multi))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_NOT_APPLICABLE);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    @Test
    void 멤버_지정이_없으면_저장하지_않고_예외가_발생한다() {
      // DTO 검증(@AssertTrue)과 별개로 서비스 방어 검증을 확인한다 — 컨트롤러를 거치지 않는 직접 호출용
      // 최후 가드다. 슬롯 목록이 비면 분철을 읽기도 전에 끊으므로 여기서는 스텁이 필요 없다.
      ParticipateRequest emptyRequest = new ParticipateRequest(null, SHIPPING_ADDRESS_ID);

      assertThatThrownBy(
              () -> participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, emptyRequest))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_REQUIRED_FIELD_MISSING);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    @Test
    void 슬롯_점유에_실패하면_예외가_발생한다() {
      Instant deadline = NOW.plus(Duration.ofDays(7));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(deadline);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      givenOpenSlot();
      givenParticipantAccount();
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      // 저장 시점 재확인에서 모집중이 아니면 false → 롤백 의도.
      given(participationDomainService.createParticipationIfRecruiting(any())).willReturn(false);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      // 호스트 계좌 조회까지 가지 않는다.
      then(userDomainService).should(never()).getUser(HOST_ID);
    }

    @Test
    void 마감이_10분_이내면_입금만료시각이_deadline으로_클램프된다() {
      Instant deadline = NOW.plus(Duration.ofMinutes(5));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(deadline);
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      givenOpenSlot();
      givenParticipantAccount();
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                // 저장 성공 시 영속화로 id 가 채워지는 것을 흉내낸다.
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      // deadline(5분 후)이 10분 창보다 빠르므로 dueAt = deadline.
      assertThat(result.dueAt()).isEqualTo(deadline);
    }

    @Test
    void 호스트가_본인_분철에_참여하면_예외가_발생한다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(HOST_ID)).willReturn(true);

      assertThatThrownBy(
              () -> participationService.participate(BUNCHEOL_ID, HOST_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    @Test
    void 프로필_미완료_유저가_참여하면_예외가_발생한다() {
      willThrow(new BusinessException(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE))
          .given(userDomainService)
          .requireProfileCompleted(PARTICIPANT_ID);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    @Test
    void 배송지가_본인_소유가_아니면_저장하지_않고_예외가_전파된다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      givenOpenSlot();
      willThrow(new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN))
          .given(participationShippingAddressResolver)
          .resolve(PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 모집중이_아니면_저장에_실패하고_예외가_발생한다() {
      Instant deadline = NOW.plus(Duration.ofDays(7));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(deadline);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      givenOpenSlot();
      givenParticipantAccount();
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any())).willReturn(false);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 모집중이_아닌_분철은_validateRecruiting에서_막힌다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING))
          .given(buncheol)
          .validateRecruiting(NOW);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }
  }

  @Nested
  @DisplayName("호스트 입금확인 테스트")
  class ConfirmPaymentTest {

    // 🔴 C2C 의 돈 단위는 참여 한 건이 아니라 묶음이다(이체 1회·배송비 1회·택배 1개). 슬롯 하나만 확정되면
    // 「제외」·자동만료·「입금 수집 종료」·묶음 확정이 동시에 전부 닫혀 그 묶음은 DB 로만 되살릴 수 있다.
    // 확정 자체가 막히는지뿐 아니라 **부수효과가 하나도 안 일어나는지**까지 본다 — 스냅샷·이벤트가 먼저
    // 나가면 차단해도 흔적이 남는다.
    @Test
    void C2C_는_슬롯_단위_입금확인을_거부한다() {
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isC2c()).willReturn(true);

      assertThatThrownBy(() -> participationService.confirmPayment(HOST_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUNDLE_CONFIRM_REQUIRED);

      // 소유자 검증은 가드보다 먼저다 — 남의 분철이면 그쪽 사유로 막혀야 하고, 플로우를 알려 주면 안 된다.
      then(buncheol).should().validateOwner(HOST_ID);
      then(buncheolDomainService).should(never()).getBuncheolForUpdate(anyLong());
      then(participationDomainService).should(never()).confirmPayment(anyLong(), any());
      then(deliverySnapshotCreator).should(never()).create(any());
      then(eventPublisher).shouldHaveNoInteractions();
    }

    // 어드민 벌크 확인은 참여 id 배열을 건별 독립 트랜잭션으로 돈다 — 중간 한 건이 실패해도 앞 건은 커밋된
    // 뒤다. 즉 운영자가 대시보드에서 한 번 잘못 고르는 것만으로 위 상태가 만들어진다.
    @Test
    void C2C_는_어드민_슬롯_단위_입금확인도_거부한다() {
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isC2c()).willReturn(true);

      assertThatThrownBy(() -> participationService.confirmPaymentByAdmin(PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUNDLE_CONFIRM_REQUIRED);

      then(buncheolDomainService).should(never()).getBuncheolForUpdate(anyLong());
      then(participationDomainService).should(never()).confirmPayment(anyLong(), any());
      then(deliverySnapshotCreator).should(never()).create(any());
      then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void 입금확인에_성공하면_배송_스냅샷을_만들고_입금확인_이벤트를_발행한다() {
      Participation participation = mock(Participation.class);
      given(participation.getId()).willReturn(PARTICIPATION_ID);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      // 아직 전 슬롯 확정 아님 → 조기확정 CAS 미전이.
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(Collections.nCopies(5, buncheolMember()));
      given(buncheolDomainService.confirmIfAllSlotsConfirmed(BUNCHEOL_ID, 5, NOW)).willReturn(false);

      participationService.confirmPayment(HOST_ID, PARTICIPATION_ID);

      then(buncheol).should().validateOwner(HOST_ID);
      then(participationDomainService).should().confirmPayment(PARTICIPATION_ID, NOW);
      // 입금확인 시점에 참여 시점 배송지로 스냅샷이 생성된다(배송지는 변경 불가).
      then(deliverySnapshotCreator).should().create(participation);
      then(eventPublisher).should().publishEvent(any(PaymentConfirmedEvent.class));
      then(buncheolConfirmedFinalizer).should(never()).finalizeConfirmed(any());
    }

    @Test
    void 관리자_입금확인은_소유권_검증_없이_수행된다() {
      Participation participation = mock(Participation.class);
      given(participation.getId()).willReturn(PARTICIPATION_ID);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(Collections.nCopies(5, buncheolMember()));
      given(buncheolDomainService.confirmIfAllSlotsConfirmed(BUNCHEOL_ID, 5, NOW)).willReturn(false);

      participationService.confirmPaymentByAdmin(PARTICIPATION_ID);

      then(buncheol).should(never()).validateOwner(anyLong());
      then(participationDomainService).should().confirmPayment(PARTICIPATION_ID, NOW);
      then(deliverySnapshotCreator).should().create(participation);
      then(eventPublisher).should().publishEvent(any(PaymentConfirmedEvent.class));
    }

    @Test
    void 호스트가_아니면_입금확인에_실패한다() {
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION))
          .given(buncheol)
          .validateOwner(HOST_ID);

      assertThatThrownBy(() -> participationService.confirmPayment(HOST_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NO_PERMISSION);

      then(participationDomainService).should(never()).confirmPayment(anyLong(), any());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 입금확인_도메인_전이가_실패하면_예외를_전파한다() {
      Participation participation = mock(Participation.class);
      given(participation.getId()).willReturn(PARTICIPATION_ID);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_DUE_PASSED))
          .given(participationDomainService)
          .confirmPayment(PARTICIPATION_ID, NOW);

      assertThatThrownBy(() -> participationService.confirmPayment(HOST_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_PAYMENT_DUE_PASSED);

      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 전_슬롯이_입금확인되면_조기확정_CAS_가_전이하고_후속처리가_수행된다() {
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      // 슬롯 5개. 매진·최소인원 판정은 CAS 내부 서브쿼리가 담당하고, 여기선 CAS 전이(true)만 stub.
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(Collections.nCopies(5, buncheolMember()));
      given(buncheolDomainService.confirmIfAllSlotsConfirmed(BUNCHEOL_ID, 5, NOW)).willReturn(true);

      participationService.confirmPayment(HOST_ID, PARTICIPATION_ID);

      then(buncheolDomainService).should().confirmIfAllSlotsConfirmed(BUNCHEOL_ID, 5, NOW);
      then(buncheolConfirmedFinalizer).should().finalizeConfirmed(BUNCHEOL_ID);
    }

    // 확정 CAS 보다 분철 행 잠금이 늦으면 같은 분철의 동시 확정끼리 참여 행 X ↔ 분철 행 X 로 교차해 데드락이 난다.
    @Test
    void 호스트_입금확인은_확정_CAS_전에_분철_행을_잠근다() {
      Participation participation = mock(Participation.class);
      given(participation.getId()).willReturn(PARTICIPATION_ID);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(Collections.nCopies(5, buncheolMember()));

      participationService.confirmPayment(HOST_ID, PARTICIPATION_ID);

      InOrder inOrder = Mockito.inOrder(buncheolDomainService, clock, participationDomainService);
      inOrder.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
      inOrder.verify(clock).instant();
      inOrder.verify(participationDomainService).confirmPayment(PARTICIPATION_ID, NOW);
      inOrder.verify(buncheolDomainService).confirmIfAllSlotsConfirmed(BUNCHEOL_ID, 5, NOW);
    }

    @Test
    void 관리자_입금확인도_확정_CAS_전에_분철_행을_잠근다() {
      Participation participation = mock(Participation.class);
      given(participation.getId()).willReturn(PARTICIPATION_ID);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(Collections.nCopies(5, buncheolMember()));

      participationService.confirmPaymentByAdmin(PARTICIPATION_ID);

      InOrder inOrder = Mockito.inOrder(buncheolDomainService, clock, participationDomainService);
      inOrder.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
      inOrder.verify(clock).instant();
      inOrder.verify(participationDomainService).confirmPayment(PARTICIPATION_ID, NOW);
    }

    @Test
    void 조기확정_CAS_가_전이하지_않으면_진행확정_후속처리를_하지_않는다() {
      // 아직 매진 아님 / 최소인원 미달 등의 판정은 CAS(어댑터 테스트)가 담당한다. 서비스는 CAS 결과(false)만 본다.
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(Collections.nCopies(5, buncheolMember()));
      given(buncheolDomainService.confirmIfAllSlotsConfirmed(BUNCHEOL_ID, 5, NOW)).willReturn(false);

      participationService.confirmPayment(HOST_ID, PARTICIPATION_ID);

      then(buncheolConfirmedFinalizer).should(never()).finalizeConfirmed(any());
    }
  }

  @Nested
  @DisplayName("자동 입금확인 테스트")
  class ConfirmPaymentBySystemTest {

    private Participation participation;
    private Buncheol buncheol;

    private Participation stubParticipation() {
      participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      return participation;
    }

    @Test
    void CAS_에_성공하면_CONFIRMED_를_반환하고_수동확인과_같은_후속처리를_수행한다() {
      Participation participation = stubParticipation();
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(true);
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(Collections.nCopies(5, buncheolMember()));
      given(buncheolDomainService.confirmIfAllSlotsConfirmed(BUNCHEOL_ID, 5, NOW)).willReturn(false);

      SystemPaymentConfirmResult result =
          participationService.confirmPaymentBySystem(PARTICIPATION_ID);

      assertThat(result).isEqualTo(SystemPaymentConfirmResult.CONFIRMED);
      then(deliverySnapshotCreator).should().create(participation);
      then(eventPublisher).should().publishEvent(any(PaymentConfirmedEvent.class));
    }

    // 웹훅은 READ COMMITTED 라 조기 확정 서브쿼리가 잠금 없이 센다 — 분철 행에서 줄을 서지 않으면 동시 웹훅끼리
    // 서로의 미커밋 확정을 못 봐 전 슬롯이 확정돼도 분철이 모집중에 남는다.
    @Test
    void 자동_입금확인은_확정_CAS_전에_분철_행을_잠근다() {
      stubParticipation();
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(true);
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(Collections.nCopies(5, buncheolMember()));

      participationService.confirmPaymentBySystem(PARTICIPATION_ID);

      InOrder inOrder = Mockito.inOrder(buncheolDomainService, clock, participationDomainService);
      inOrder.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
      inOrder.verify(clock).instant();
      inOrder.verify(participationDomainService).confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW);
      inOrder.verify(buncheolDomainService).confirmIfAllSlotsConfirmed(BUNCHEOL_ID, 5, NOW);
    }

    @Test
    void 이미_확정된_참여면_ALREADY_CONFIRMED_를_반환하고_부수효과를_다시_일으키지_않는다() {
      // 웹훅 재전송·운영자 수동확인 선행 시나리오. 오류로 응답하면 발신 측이 재전송을 반복한다.
      Participation participation = stubParticipation();
      given(participationDomainService.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(false);
      given(participation.getStatus()).willReturn(ParticipationStatus.CONFIRMED);

      SystemPaymentConfirmResult result =
          participationService.confirmPaymentBySystem(PARTICIPATION_ID);

      assertThat(result).isEqualTo(SystemPaymentConfirmResult.ALREADY_CONFIRMED);
      then(deliverySnapshotCreator).should(never()).create(any());
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 기한이_지나_취소된_참여면_NOT_CONFIRMABLE_을_반환한다() {
      // 돈은 들어왔는데 참여가 없는 상태. 호출 측이 운영자에게 알려 환불 판단을 받게 한다.
      Participation participation = stubParticipation();
      given(participationDomainService.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(false);
      given(participation.getStatus()).willReturn(ParticipationStatus.CANCELLED);

      SystemPaymentConfirmResult result =
          participationService.confirmPaymentBySystem(PARTICIPATION_ID);

      assertThat(result).isEqualTo(SystemPaymentConfirmResult.NOT_CONFIRMABLE);
      then(deliverySnapshotCreator).should(never()).create(any());
      then(eventPublisher).should(never()).publishEvent(any());
    }
  }

  @Nested
  @DisplayName("C2C 신청 정원 충족(입금 단계 시작) 테스트")
  class C2cBuncheolFullTest {

    private Buncheol stubC2cRecruitingBuncheol() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.RECRUITING);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolMemberDomainService.getBuncheolMemberForShare(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });
      givenParticipantAccount();
      return buncheol;
    }

    // 정원 충족 판정(열린 자리 = 살아 있는 참여, 최소 인원)은 PaymentStarter 가 한다(PaymentStarterTest). 신청 경로는 잠금으로
    // 읽은 분철을 넘겨 같은 트랜잭션에서 판정을 맡긴다 — 마지막 열린 자리를 채운 요청 안에서 입금 단계가 시작된다.
    @Test
    void 신청_뒤_잠금으로_읽은_분철로_입금_단계_시작_판정을_맡긴다() {
      Buncheol buncheol = stubC2cRecruitingBuncheol();

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(eventPublisher).should().publishEvent(any(ParticipationCreatedEvent.class));
      then(paymentStarter).should().startIfFull(buncheol, NOW);
      // 응답 모양은 그대로다 — 입금 단계가 시작돼도 계좌·기한은 알림톡으로 안내한다.
      assertThat(result.dueAt()).isNull();
      assertThat(result.hostAccount()).isNull();
    }

    // 중간 슬롯에서는 남은 슬롯이 아직 비어 있어 정원이 찰 수 없다 — 비싼 판정을 마지막 슬롯에서 한 번만 돌린다.
    @Test
    void 다슬롯_신청은_마지막_슬롯에서만_입금_단계_시작을_판정한다() {
      Buncheol buncheol = stubC2cRecruitingBuncheol();
      given(buncheolMemberDomainService.getBuncheolMemberForShare(SECOND_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember(SECOND_MEMBER_ID, SECOND_MEMBER_PRICE));

      participationService.participate(
          BUNCHEOL_ID,
          PARTICIPANT_ID,
          new ParticipateRequest(
              null, List.of(BUNCHEOL_MEMBER_ID, SECOND_MEMBER_ID), SHIPPING_ADDRESS_ID, null, null));

      then(paymentStarter).should(times(1)).startIfFull(buncheol, NOW);
    }

    // 추가 모집은 없어졌다 — 입금 단계가 시작된 분철의 빈 자리는 「마감」이고 신청은 BCH-060 이다.
    @Test
    void 입금_수집_중_분철_신청은_거절하고_입금_단계_판정도_하지_않는다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.PAYMENT_COLLECTING);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolMemberDomainService.getBuncheolMemberForShare(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
      then(paymentStarter).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("묶음 쓰기 경로 테스트 (P2-b)")
  class BundleWritePathTest {

    private static final Long EXISTING_BUNDLE_ID = 700L;
    private static final Long INHERITED_ADDRESS_ID = 201L;
    private static final Long STALE_COPY_ADDRESS_ID = 999L;
    private static final RefundAccount INHERITED_REFUND_ACCOUNT =
        RefundAccount.of("신한", "99998888", "옛이름");

    private Buncheol stubC2c(final BuncheolStatus status) {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getStatus()).willReturn(status);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolMemberDomainService.getBuncheolMemberForShare(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      return buncheol;
    }

    /** 신청(APPLIED) INSERT 성공 + 정원 충족 판정에 필요한 최소 스텁. */
    private void givenAppliedInsert() {
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });
    }

    private void givenFirstParticipation(final Buncheol buncheol) {
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      givenParticipantAccount();
    }

    /** 상속 분기의 전제가 되는 「이미 있는 활성 참여」. 스텁 없이 값만 만든다. */
    private Participation existingActive(final Long bundleId) {
      Participation existing = newInstance(Participation.class);
      setField(existing, "id", 499L);
      // 🔴 참여 사본에는 <b>다른 값</b>을 심는다. 같은 값이면 묶음을 읽든 사본을 읽든 초록이라
      // 이관이 됐는지 테스트가 말해 주지 못한다 — 사본은 신규 행에서 어차피 NULL 이 된다.
      setField(existing, "shippingAddressId", STALE_COPY_ADDRESS_ID);
      setField(existing, "bundleId", bundleId);
      return existing;
    }

    /**
     * 같은 분철에 이미 활성 참여가 있는 상태 — 배송지·입금자명 상속 분기의 전제.
     *
     * <p>⚠️ {@code lenient()} 인 이유: 추가 모집 경로는 이 조회를 <b>아예 하지 않는다</b>. 그래도 깔아 두지 않으면
     * "상속 후보가 있는데도 상속하지 않는다" 를 검증할 수 없고, 단언이 배제할 값(옛 배송지·옛 이름·0원)이
     * 테스트 안에 존재하지 않아 <b>변경 전 코드로도 통과</b>한다.
     */
    private void givenExistingActive(final Long bundleId) {
      Participation existing = existingActive(bundleId);
      lenient()
          .when(
              // 스텁의 조건을 실제 게이트(회원 개최·모집중)와 같게 맞춘다.
              participationDomainService.findInheritanceSource(
                  argThat(
                      b -> b != null && b.isC2c() && b.getStatus() == BuncheolStatus.RECRUITING),
                  eq(PARTICIPANT_ID)))
          .thenReturn(Optional.of(existing));
      // 배송지 정본은 묶음이다. 사본이 아니라 이 값이 상속돼야 한다.
      lenient()
          .when(participationBundleDomainService.requireInheritedShippingAddressId(existing))
          .thenReturn(INHERITED_ADDRESS_ID);
    }

    // LEGACY 는 1인 1활성슬롯이라 묶을 것이 없다 — 백필 STEP 1(행별 1:1)과 같은 규칙이어야 한다.
    @Test
    void LEGACY_참여는_항상_새_묶음을_연다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(NOW.plus(Duration.ofDays(7)));
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      givenOpenSlot();
      givenParticipantAccount();
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      // dueAt 을 any() 로 두면 null 로 바뀌어도 통과한다 — 그러면 bundles.due_at 이 영구 NULL 이 되어
      // 「제외」 기한 가드가 fail-open 된다. 즉시 입금 경로는 값이 있어야 하므로 정확히 고정한다.
      then(participationBundleDomainService)
          .should()
          .attach(
              any(),
              isNull(),
              eq(SHIPPING_ADDRESS_ID),
              eq(SHIPPING_FEE),
              any(),
              eq(NOW.plus(Duration.ofMinutes(10))),
              eq(NOW));
    }

    // 모집중 재참여는 같은 이체·같은 택배다.
    @Test
    void C2C_모집중_재참여는_기존_묶음을_재사용한다() {
      stubC2c(BuncheolStatus.RECRUITING);
      givenExistingActive(EXISTING_BUNDLE_ID);
      // 재사용이면 계좌 스냅샷을 뜨지 않는다 — 그 묶음이 이미 가진 계좌가 정본이다 (P2-c).
      givenAppliedInsert();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(participationBundleDomainService)
          .should()
          .attach(
              any(), eq(EXISTING_BUNDLE_ID), eq(INHERITED_ADDRESS_ID), eq(0L),
              // 재사용이면 계좌를 아예 안 넘긴다 — any() 로 두면 스냅샷을 떠도 통과한다.
              isNull(), isNull(), eq(false), eq(NOW));
      // 그래서 유저 조회 헛쿼리도 없다.
      then(userDomainService).should(never()).getUser(PARTICIPANT_ID);
    }

    // 🔴 다슬롯 <b>성공</b> 케이스. 이 트랙에서 가장 값진 단언이다 — 지금까지 다슬롯 테스트는 전부
    // 「거절」이라, 응답 총액이 배송비를 슬롯 수만큼 곱해도 스위트가 초록으로 통과했다.
    //
    // 묶음 = 이체 1회 · 택배 1개다. 자리를 2개 잡아도 배송비는 <b>1회</b>여야 한다. 응답 총액을 묶음의
    // 배송비로 계산하면(= 각 슬롯이 만액을 들고 합산되면) 참여자가 안내받은 금액이 실제보다 커지고,
    // 그 돈을 그대로 보내면 개최자 통장에 초과 입금이 된다.
    @Test
    void C2C_자리_2개_신청의_응답_총액은_배송비를_한_번만_더한다() {
      Buncheol buncheol = stubC2c(BuncheolStatus.RECRUITING);
      givenFirstParticipation(buncheol);
      given(buncheolMemberDomainService.getBuncheolMemberForShare(SECOND_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember(SECOND_MEMBER_ID, SECOND_MEMBER_PRICE));
      // 첫 슬롯은 활성 참여가 없고, 둘째 슬롯은 첫 슬롯을 상속한다(같은 묶음 · 배송비 0).
      given(
              participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.empty())
          .willReturn(Optional.of(existingActive(EXISTING_BUNDLE_ID)));
      givenAppliedInsert();

      ParticipateResult result =
          participationService.participate(
              BUNCHEOL_ID,
              PARTICIPANT_ID,
              new ParticipateRequest(
                  null,
                  List.of(BUNCHEOL_MEMBER_ID, SECOND_MEMBER_ID),
                  SHIPPING_ADDRESS_ID,
                  null,
                  null));

      assertThat(result.totalAmount())
          .isEqualTo(MEMBER_PRICE + SECOND_MEMBER_PRICE + SHIPPING_FEE);
    }

    @Test
    void C2C_첫_신청은_새_묶음을_연다() {
      Buncheol buncheol = stubC2c(BuncheolStatus.RECRUITING);
      givenFirstParticipation(buncheol);
      given(
              participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.empty());
      givenAppliedInsert();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(participationBundleDomainService)
          .should()
          .attach(
              any(), isNull(), eq(SHIPPING_ADDRESS_ID), eq(SHIPPING_FEE), any(), isNull(), eq(false),
              eq(NOW));
    }

    // 계좌 강제(PR #151) 이전에 만들어진 활성 C2C 참여를 가진 사람은 프로필 계좌가 없을 수 있다.
    // 재사용이면 그 묶음의 계좌가 정본이라 계좌가 필요 없는데, 스냅샷을 뜨면 USER_BANK_ACCOUNT_NOT_REGISTERED
    // 로 재참여가 새로 막힌다.
    @Test
    void 계좌_미등록_유저도_모집중_재참여는_막히지_않는다() {
      stubC2c(BuncheolStatus.RECRUITING);
      givenExistingActive(EXISTING_BUNDLE_ID);
      givenAppliedInsert();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(participationBundleDomainService)
          .should()
          .attach(
              any(), eq(EXISTING_BUNDLE_ID), any(), eq(0L), isNull(), isNull(), eq(false), eq(NOW));
    }

    // 배포선 창에서 생긴 미연결 행(bundle_id NULL)이 재사용 후보가 되는 경우. 새로 열되
    // ⚠️ 배송비는 상속분(0)을 그대로 써야 한다 — "새 묶음이니 부과" 로 재계산하면 없던 과금이 생긴다.
    @Test
    void 재사용_후보의_묶음이_비어_있으면_새로_열되_배송비는_상속분을_쓴다() {
      stubC2c(BuncheolStatus.RECRUITING);
      givenExistingActive(null);
      givenParticipantAccount();
      givenAppliedInsert();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(participationBundleDomainService)
          .should()
          .attach(
              any(), isNull(), eq(INHERITED_ADDRESS_ID), eq(0L), any(), isNull(), eq(false), eq(NOW));
    }
  }

  @Nested
  @DisplayName("재참여 상속·상태 가드 테스트 (docs/80 §3-6)")
  class AdditionalRoundShippingFeeTest {

    private static final Long INHERITED_ADDRESS_ID = 201L;
    private static final Long STALE_COPY_ADDRESS_ID = 999L;
    private static final Long INHERITED_BUNDLE_ID = 700L;
    private static final RefundAccount INHERITED_REFUND_ACCOUNT =
        RefundAccount.of("신한", "99998888", "옛이름");

    private Buncheol stubC2c(final BuncheolStatus status) {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getStatus()).willReturn(status);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolMemberDomainService.getBuncheolMemberForShare(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      return buncheol;
    }

    /**
     * 같은 분철에 이미 활성 참여가 있다 — 상속 분기의 전제.
     *
     * <p>⚠️ {@code lenient()} 인 이유: 상태 가드에 막히는 경로는 이 조회를 <b>아예 하지 않는다</b>.
     */
    private void givenExistingActive() {
      Participation existing = newInstance(Participation.class);
      setField(existing, "id", 499L);
      // 🔴 참여 사본에는 <b>다른 값</b>을 심는다. 같은 값이면 묶음을 읽든 사본을 읽든 초록이라
      // 이관이 됐는지 테스트가 말해 주지 못한다 — 사본은 신규 행에서 어차피 NULL 이 된다.
      setField(existing, "shippingAddressId", STALE_COPY_ADDRESS_ID);
      setField(existing, "bundleId", INHERITED_BUNDLE_ID);
      lenient()
          .when(
              // 스텁의 조건을 실제 게이트(회원 개최·모집중)와 같게 맞춘다.
              participationDomainService.findInheritanceSource(
                  argThat(
                      b -> b != null && b.isC2c() && b.getStatus() == BuncheolStatus.RECRUITING),
                  eq(PARTICIPANT_ID)))
          .thenReturn(Optional.of(existing));
      // 배송지 정본은 묶음이다. 사본이 아니라 이 값이 상속돼야 한다.
      lenient()
          .when(participationBundleDomainService.requireInheritedShippingAddressId(existing))
          .thenReturn(INHERITED_ADDRESS_ID);
    }


    // 상태 판정이 스냅샷 계산보다 뒤에 있으면, 모집이 끝난 분철에 재참여할 때 배송지·계좌 예외가
    // 먼저 나가 "왜 계좌 얘기가 나오지" 가 된다. 재참여자는 이 PR 로 그 경로에 새로 편입된다.
    @Test
    void 모집이_끝난_분철은_배송지_계좌를_보기_전에_거절한다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.CONFIRMED);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolMemberDomainService.getBuncheolMemberForShare(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(participationShippingAddressResolver).should(never()).resolve(any(), any(), any());
      then(userDomainService).should(never()).getUser(anyLong());
      // 상속 조회도 헛돌지 않는다 — 상태 예외가 그보다 먼저다.
      then(participationDomainService).should(never()).findInheritanceSource(any(), anyLong());
    }

    // 모집중 재참여는 한 번의 이체·한 개의 택배다 — 상속과 0원이 유지돼야 한다(회귀 방지).
    @Test
    void 모집중_재참여는_배송지와_입금자명을_상속하고_배송비를_부과하지_않는다() {
      stubC2c(BuncheolStatus.RECRUITING);
      givenExistingActive();
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(participationDomainService)
          .should()
          .createParticipationIfRecruiting(participationCaptor.capture());
      Participation saved = participationCaptor.getValue();
      assertThat(saved.getShippingFee()).isZero();
      assertThat(saved.getShippingAddressId()).isEqualTo(INHERITED_ADDRESS_ID);
      // 🔴 입금자명 상속은 이제 값을 복사하는 것이 아니라 <b>묶음을 공유하는 것</b>으로 성립한다 (P2-c).
      // 기존 묶음을 재사용하므로 그 묶음이 이미 가진 예금주가 그대로 남는다 (docs/46 §4.7-A2).
      then(participationBundleDomainService)
          .should()
          .attach(any(), eq(INHERITED_BUNDLE_ID), eq(INHERITED_ADDRESS_ID), eq(0L), any(), isNull(),
              eq(false), eq(NOW));
      // 상속 구간에서는 배송지 검증을 아예 타지 않는다(요청 입력을 무시한다).
      then(participationShippingAddressResolver).should(never()).resolve(any(), any(), any());
    }
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

  @Nested
  @DisplayName("참여자 자발 취소 — 안내 문구 분기 (docs/54 4-2)")
  class CancelByParticipantErrorTest {

    private Participation participationWith(final ParticipationStatus status) {
      Participation participation = mock(Participation.class);
      given(participation.getStatus()).willReturn(status);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      return participation;
    }

    // LEGACY 는 취소 경로가 없다 — 범용 가드(BCH-084)가 아니라 "기한 만료로 자동 취소된다"는
    // 전용 안내를 준다. 다른 C2C 액션들은 계속 BCH-084 를 쓴다.
    @Test
    void LEGACY_입금대기_참여는_취소_전용_코드로_막힌다() {
      Participation participation = participationWith(ParticipationStatus.AWAITING_PAYMENT);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participation.getFlowType()).willReturn(FlowType.LEGACY);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isStaff()).willReturn(true);

      assertThatThrownBy(
              () -> participationService.cancelByParticipant(PARTICIPANT_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CANCEL_NOT_SUPPORTED);
      // 거절할 운영진 참여 요청으로 분철 행 잠금 줄을 세우지 않는다(정각 오픈 때 신청이 그 줄에 선다).
      then(buncheolDomainService).should(never()).getBuncheolForUpdate(anyLong());
    }

    // 확정된 참여는 만료 CAS(AWAITING_PAYMENT 한정)가 걸리지 않아 "기한이 지나면 자동 취소돼요"가
    // 사실이 아니다. 플로우 가드보다 상태 검사를 먼저 태워 문의 경유로 보낸다.
    @Test
    void 확정된_참여는_플로우와_무관하게_문의_경유로_안내한다() {
      participationWith(ParticipationStatus.CONFIRMED);

      assertThatThrownBy(
              () -> participationService.cancelByParticipant(PARTICIPANT_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED);
    }
  }

  @Nested
  @DisplayName("참여자 자발 취소 — 회원 개최는 신청만")
  class CancelMemberParticipationTest {

    private void participation(final ParticipationStatus status) {
      Participation participation = mock(Participation.class);
      given(participation.getStatus()).willReturn(status);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participation.getFlowType()).willReturn(FlowType.C2C);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);

      // 회원 개최 참여 취소는 분철 행을 첫 잠금으로 잡는다.
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isStaff()).willReturn(false);
    }

    // 입금 단계가 시작되면 개최자가 인원을 셈한 뒤라 스스로 빠질 수 없다 — 진행 확정 때 입금 미확인으로 빠진다.
    @Test
    void 입금_대기_참여는_자발_취소를_막는다() {
      participation(ParticipationStatus.AWAITING_PAYMENT);

      assertThatThrownBy(
              () -> participationService.cancelByParticipant(PARTICIPANT_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED);

      then(participationDomainService).should(never()).cancelByUser(anyLong(), any());
    }

    @Test
    void 신청_참여는_그대로_취소된다() {
      participation(ParticipationStatus.APPLIED);
      given(participationDomainService.cancelByUser(PARTICIPATION_ID, NOW)).willReturn(true);

      participationService.cancelByParticipant(PARTICIPANT_ID, PARTICIPATION_ID);

      then(participationDomainService).should().cancelByUser(PARTICIPATION_ID, NOW);
      // 마지막 슬롯이면 묶음도 끝난다 — 안 닫으면 재참여가 시체 묶음을 재사용해 택배가 옛 주소로 나간다.
      then(participationBundleDomainService).should().closeIfEmpty(any(), eq(NOW));
    }

    // 판정과 CAS 사이에 입금 단계가 시작되면 CAS(신청만)가 0행이다 — 분철 행을 먼저 잠가 실제로는 오지 않지만 마지막 선이다.
    @Test
    void 취소_CAS_가_0행이면_상태_위반으로_거절한다() {
      participation(ParticipationStatus.APPLIED);
      given(participationDomainService.cancelByUser(PARTICIPATION_ID, NOW)).willReturn(false);

      assertThatThrownBy(
              () -> participationService.cancelByParticipant(PARTICIPANT_ID, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED);

      then(participationBundleDomainService).should(never()).closeIfEmpty(any(), any());
    }
  }

  @Nested
  @DisplayName("운영진 분철 예약 오픈 테스트")
  class StaffOpenTest {

    private final Instant deadline = NOW.plus(Duration.ofDays(7));

    private StaffBuncheolSetting settingOpeningAt(final Instant opensAt) {
      return StaffBuncheolSetting.create(
          BUNCHEOL_ID, opensAt, deadline, NOW.minus(Duration.ofDays(1)));
    }

    private Buncheol givenStaffBuncheolWithSetting(final Optional<StaffBuncheolSetting> setting) {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      givenOpenSlot();
      given(staffBuncheolSettingDomainService.findSetting(BUNCHEOL_ID)).willReturn(setting);
      return buncheol;
    }

    private void givenReadyToInsert(final Buncheol buncheol) {
      given(buncheol.getDeadline()).willReturn(deadline);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      givenParticipantAccount();
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
    }

    private void givenHostAccount(final Buncheol buncheol) {
      given(buncheol.getHostId()).willReturn(HOST_ID);
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);
    }

    private static Boolean savedWithId(final InvocationOnMock invocation) {
      setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
      return true;
    }

    @Test
    void 오픈_전_코드_없는_참여는_BCH_128_이고_쓰기_전에_실패한다() {
      givenStaffBuncheolWithSetting(Optional.of(settingOpeningAt(NOW.plusSeconds(1))));

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_OPEN_YET);

      then(participationShippingAddressResolver).should(never()).resolve(any(), any(), any());
      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
      then(participationDomainService).should(never()).createParticipationIfRecruitingAndOpen(any());
    }

    @Test
    void 오픈_뒤에는_오픈_조건_INSERT_로_받고_기한은_참여_후_10분이다() {
      Buncheol buncheol = givenStaffBuncheolWithSetting(Optional.of(settingOpeningAt(NOW)));
      givenReadyToInsert(buncheol);
      givenHostAccount(buncheol);
      given(participationDomainService.createParticipationIfRecruitingAndOpen(any()))
          .willAnswer(StaffOpenTest::savedWithId);

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      assertThat(result.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
      then(staffBuncheolSettingDomainService).should(never()).isBeforeOpenByDbClock(any());
    }

    @Test
    void 설정_행이_없으면_지금_INSERT_로_받는다() {
      Buncheol buncheol = givenStaffBuncheolWithSetting(Optional.empty());
      givenReadyToInsert(buncheol);
      givenHostAccount(buncheol);
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(StaffOpenTest::savedWithId);

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(participationDomainService).should(never()).createParticipationIfRecruitingAndOpen(any());
      then(staffBuncheolSettingDomainService).should(never()).isBeforeOpenByDbClock(any());
    }

    // 앱 시각으로는 열렸는데 DB 시각으로는 아직인 정각 직후 몇 ms — 모집 종료(BCH-060)로 잘못 나가면 안 된다.
    @Test
    void 오픈_조건_INSERT_가_0행이고_DB_시각으로_오픈_전이면_BCH_128() {
      Buncheol buncheol = givenStaffBuncheolWithSetting(Optional.of(settingOpeningAt(NOW)));
      givenReadyToInsert(buncheol);
      given(participationDomainService.createParticipationIfRecruitingAndOpen(any()))
          .willReturn(false);
      given(staffBuncheolSettingDomainService.isBeforeOpenByDbClock(BUNCHEOL_ID)).willReturn(true);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_OPEN_YET);
    }

    @Test
    void 오픈_조건_INSERT_가_0행이고_DB_시각으로도_열렸으면_BCH_060() {
      Buncheol buncheol = givenStaffBuncheolWithSetting(Optional.of(settingOpeningAt(NOW)));
      givenReadyToInsert(buncheol);
      given(participationDomainService.createParticipationIfRecruitingAndOpen(any()))
          .willReturn(false);
      given(staffBuncheolSettingDomainService.isBeforeOpenByDbClock(BUNCHEOL_ID)).willReturn(false);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    @Test
    void 설정_행이_없는_분철의_INSERT_0행은_DB_시각을_보지_않고_BCH_060() {
      Buncheol buncheol = givenStaffBuncheolWithSetting(Optional.empty());
      givenReadyToInsert(buncheol);
      given(participationDomainService.createParticipationIfRecruiting(any())).willReturn(false);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(staffBuncheolSettingDomainService).should(never()).isBeforeOpenByDbClock(any());
    }

    @Test
    void 회원_개최_신청은_오픈_설정을_읽지_않는다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.getStatus()).willReturn(BuncheolStatus.RECRUITING);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolMemberDomainService.getBuncheolMemberForShare(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(StaffOpenTest::savedWithId);
      givenParticipantAccount();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(staffBuncheolSettingDomainService).shouldHaveNoInteractions();
      then(participationDomainService).should(never()).createParticipationIfRecruitingAndOpen(any());
    }
  }

  @Nested
  @DisplayName("참여 코드(서포터즈 배정 슬롯) 테스트")
  class CodeParticipationTest {

    private BuncheolMember codeSlotMember() {
      return codeSlotMember(0L);
    }

    private BuncheolMember codeSlotMember(final long price) {
      BuncheolMember member = buncheolMember(BUNCHEOL_MEMBER_ID, price);
      setField(member, "accessType", BuncheolMemberAccessType.CODE_ONLY);
      return member;
    }

    private ParticipationCode participationCode() {
      ParticipationCode code =
          ParticipationCode.issue(
              "ABCD2345",
              BUNCHEOL_ID,
              BUNCHEOL_MEMBER_ID,
              "@supporter",
              NOW.plus(Duration.ofHours(48)),
              NOW);
      setField(code, "id", 7L);
      return code;
    }

    private ParticipateRequest codeRequest() {
      return new ParticipateRequest(BUNCHEOL_MEMBER_ID, SHIPPING_ADDRESS_ID, "ABCD2345");
    }

    private ParticipationCode givenCodeParticipation() {
      return givenCodeParticipation(0L);
    }

    private ParticipationCode givenCodeParticipation(final long slotPrice) {
      Buncheol buncheol = mock(Buncheol.class);
      ParticipationCode code = participationCode();
      givenParticipantAccount();
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(NOW.plus(Duration.ofDays(30)));
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(codeSlotMember(slotPrice));
      given(participationCodeDomainService.validateForParticipation(any(), eq("ABCD2345"), eq(NOW)))
          .willReturn(Optional.of(code));
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });
      given(buncheolMemberDomainService.findAllByBuncheolId(BUNCHEOL_ID))
          .willReturn(List.of(codeSlotMember(slotPrice)));
      return code;
    }

    // 배송비가 남으면 배송비 환급 이벤트 대상으로 잡혀 없는 환급 CTA 가 붙는다.
    @Test
    void 코드_참여는_배송비를_부과하지_않는다() {
      givenCodeParticipation();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest());

      then(participationDomainService)
          .should()
          .createParticipationIfRecruiting(participationCaptor.capture());
      Participation saved = participationCaptor.getValue();
      assertThat(saved.getAmount()).isZero();
      assertThat(saved.getShippingFee()).isZero();
    }

    // 코드 전용 자리는 예약 오픈 전에도 받는다(주헌 자리). 오픈 설정을 읽지 않고 지금 INSERT 로 간다.
    @Test
    void 코드_참여는_오픈_설정을_보지_않고_지금_INSERT_로_받는다() {
      givenCodeParticipation();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest());

      then(staffBuncheolSettingDomainService).shouldHaveNoInteractions();
      then(participationDomainService).should().createParticipationIfRecruiting(any());
      then(participationDomainService).should(never()).createParticipationIfRecruitingAndOpen(any());
    }

    // 0원이라 환불할 돈은 없지만 예금주가 개최자 통장 대조 키이고, 참여 묶음이 계좌를 NOT NULL 로 요구한다 (docs/80 결정 1).
    @Test
    void 코드_참여도_환불_계좌를_스냅샷한다() {
      givenCodeParticipation();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest());

      then(participationDomainService).should().createParticipationIfRecruiting(any());
      then(participationBundleDomainService)
          .should()
          .attach(
              any(),
              isNull(),
              any(),
              eq(0L),
              eq(
                  RefundAccount.of(
                      PARTICIPANT_ACCOUNT.bank(),
                      PARTICIPANT_ACCOUNT.account(),
                      PARTICIPANT_ACCOUNT.holder())),
              any(),
              eq(NOW));
    }

    @Test
    void 정산_계좌가_없으면_0원_코드_참여도_거부한다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(NOW.plus(Duration.ofDays(30)));
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(codeSlotMember());
      given(participationCodeDomainService.validateForParticipation(any(), eq("ABCD2345"), eq(NOW)))
          .willReturn(Optional.of(participationCode()));
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(mock(User.class));

      assertThatThrownBy(
              () -> participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);

      // 계좌 검증은 참여 INSERT 앞이라 코드도 소모되지 않는다.
      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
      then(participationCodeDomainService).should(never()).consume(any(), anyLong(), any());
    }

    @Test
    void 코드_참여는_결제_구간을_건너뛰고_즉시_확정된다() {
      givenCodeParticipation();

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest());

      then(participationDomainService).should().confirmPayment(PARTICIPATION_ID, NOW);
      then(deliverySnapshotCreator).should().create(any());
      assertThat(result.totalAmount()).isZero();
      assertThat(result.dueAt()).isNull();
      assertThat(result.hostAccount()).isNull();
      // 참여자 계좌 스냅샷 1회뿐 — 개최자 계좌는 안내할 일이 없어 조회하지 않는다 (docs/80 결정 1).
      // getUser(HOST_ID) never 만으로는 부족하다: 이 테스트의 buncheol 은 getHostId() 를 스텁하지 않아
      // 개최자 조회가 되살아나도 인자가 null 이라 그 어서션은 공허하게 통과한다.
      then(userDomainService).should(times(1)).getUser(PARTICIPANT_ID);
      then(userDomainService).should(never()).getUser(HOST_ID);
    }

    @Test
    void 유료_코드_전용_자리도_코드로_참여하면_0원으로_바로_확정된다() {
      givenCodeParticipation(15_000L);

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest());

      then(participationDomainService)
          .should()
          .createParticipationIfRecruiting(participationCaptor.capture());
      Participation saved = participationCaptor.getValue();
      assertThat(saved.getAmount()).isZero();
      assertThat(saved.getShippingFee()).isZero();
      then(participationDomainService).should().confirmPayment(PARTICIPATION_ID, NOW);
      assertThat(result.totalAmount()).isZero();
      assertThat(result.dueAt()).isNull();
      assertThat(result.hostAccount()).isNull();
    }

    @Test
    void 유료_코드_전용_자리의_코드_참여도_분철_X_락을_먼저_잡는다() {
      givenCodeParticipation(15_000L);

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest());

      InOrder inOrder = Mockito.inOrder(buncheolDomainService, participationDomainService);
      inOrder.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
      inOrder.verify(participationDomainService).createParticipationIfRecruiting(any());
    }

    @Test
    void _0원_참여는_분철_X_락을_먼저_잡는다() {
      givenCodeParticipation();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest());

      InOrder inOrder = Mockito.inOrder(buncheolDomainService, participationDomainService);
      inOrder.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
      inOrder.verify(participationDomainService).createParticipationIfRecruiting(any());
    }

    @Test
    void 코드는_참여_생성_이후에_소모된다() {
      ParticipationCode code = givenCodeParticipation();

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest());

      InOrder inOrder = Mockito.inOrder(participationDomainService, participationCodeDomainService);
      inOrder.verify(participationDomainService).createParticipationIfRecruiting(any());
      inOrder.verify(participationCodeDomainService).consume(code, PARTICIPATION_ID, NOW);
    }

    @Test
    void 코드_검증이_실패하면_참여를_생성하지_않는다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(codeSlotMember());
      willThrow(new BusinessException(ErrorCode.PARTICIPATION_CODE_EXPIRED))
          .given(participationCodeDomainService)
          .validateForParticipation(any(), any(), any());

      assertThatThrownBy(
              () -> participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, codeRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_EXPIRED);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }
  }

  @Nested
  @DisplayName("개최자가 뺀 자리로 신청 테스트")
  class RemovedSlotTest {

    private Buncheol stubC2cBuncheol() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
      return buncheol;
    }

    // 자리를 빼면 행이 사라진다 — 분철 락 뒤 자리 조회가 없는 멤버로 거부해 INSERT 까지 가지 않는다.
    @Test
    void 뺀_자리로_신청하면_없는_멤버로_거부하고_참여를_만들지_않는다() {
      stubC2cBuncheol();
      given(buncheolMemberDomainService.getBuncheolMemberForShare(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willThrow(new BusinessException(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND));

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    // 🔴 다슬롯 신청은 한 트랜잭션이다 — 빠진 자리 하나의 예외가 그대로 밖으로 나가야 앞서 만든 자리까지 롤백된다.
    @Test
    void 다슬롯_신청_중_하나가_빠졌으면_예외가_전파되어_전체가_실패한다() {
      Buncheol buncheol = stubC2cBuncheol();
      given(buncheol.getStatus()).willReturn(BuncheolStatus.RECRUITING);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMemberForShare(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      given(buncheolMemberDomainService.getBuncheolMemberForShare(SECOND_MEMBER_ID, BUNCHEOL_ID))
          .willThrow(new BusinessException(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND));
      given(participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.empty());
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      givenParticipantAccount();
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID,
                      PARTICIPANT_ID,
                      new ParticipateRequest(
                          null,
                          List.of(BUNCHEOL_MEMBER_ID, SECOND_MEMBER_ID),
                          SHIPPING_ADDRESS_ID,
                          null,
                          null)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND);

      then(participationDomainService).should(times(1)).createParticipationIfRecruiting(any());
      // 마지막 슬롯에서 실패했으므로 정원 충족 판정까지 가지 않는다.
      then(buncheolMemberDomainService).should(never()).findAllByBuncheolIdForShare(anyLong());
    }
  }

  private BuncheolMember closedMember(final Long id) {
    BuncheolMember member = buncheolMember(id, MEMBER_PRICE);
    setField(member, "accessType", BuncheolMemberAccessType.CLOSED);
    return member;
  }

  @Nested
  @DisplayName("개최자가 닫은 자리로 신청 테스트")
  class ClosedSlotTest {

    private Buncheol stubC2cBuncheol() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
      return buncheol;
    }

    // 🔴 자리는 잠금 읽기로 읽어야 한다 — 이 트랜잭션의 스냅샷은 분철 락 전에 잡혀, 일반 조회로는 락을 기다리는 사이
    // 개최자가 닫은 자리를 「열림」으로 본다. 일반 조회로 되돌리면 이 테스트가 잡는다.
    @Test
    void C2C_신청은_닫힌_자리를_잠금_읽기로_확인해_마감_멤버로_거부한다() {
      stubC2cBuncheol();
      given(
              buncheolMemberDomainService.getBuncheolMemberForShare(
                  BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(closedMember(BUNCHEOL_MEMBER_ID));

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_CLOSED);

      then(buncheolMemberDomainService).should(never()).getBuncheolMember(any(), any());
      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    // 다슬롯은 한 트랜잭션이다 — 닫힌 자리의 예외가 그대로 나가야 앞서 만든 자리까지 롤백된다.
    @Test
    void 다슬롯_신청_중_하나가_닫혔으면_예외가_전파되어_전체가_실패한다() {
      Buncheol buncheol = stubC2cBuncheol();
      given(buncheol.getStatus()).willReturn(BuncheolStatus.RECRUITING);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(
              buncheolMemberDomainService.getBuncheolMemberForShare(
                  BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      given(
              buncheolMemberDomainService.getBuncheolMemberForShare(
                  SECOND_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(closedMember(SECOND_MEMBER_ID));
      given(participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.empty());
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      givenParticipantAccount();
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID,
                      PARTICIPANT_ID,
                      new ParticipateRequest(
                          null,
                          List.of(BUNCHEOL_MEMBER_ID, SECOND_MEMBER_ID),
                          SHIPPING_ADDRESS_ID,
                          null,
                          null)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_CLOSED);

      then(participationDomainService).should(times(1)).createParticipationIfRecruiting(any());
    }

    // LEGACY 에는 닫힌 자리가 생기지 않지만(개최자 빼기는 C2C 전용) 데이터 이상이면 같은 코드로 막는다.
    @Test
    void LEGACY_신청도_닫힌_자리면_마감_멤버로_거부한다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(closedMember(BUNCHEOL_MEMBER_ID));

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_CLOSED);

      then(participationCodeDomainService)
          .should(never())
          .validateForParticipation(any(), any(), any());
      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }
  }

  @Nested
  @DisplayName("C2C 신청 — 분철 락 뒤 최신 값 테스트")
  class LatestValueAfterLockTest {

    private static final long CHANGED_SHIPPING_FEE = 2_000L;

    /**
     * 운영에서는 락 이전에 읽힌 분철과 락 뒤 분철이 <b>같은 인스턴스</b>다(영속성 컨텍스트) — 다시 읽기가 그 인스턴스의 값을 덮는다.
     * 그래서 mock 도 하나만 두고, 다시 읽기 호출({@code getLatestBuncheolForUpdate})에서 상태·배송비를 바꿔 「다시 채움」을 흉내
     * 낸다. mock 을 둘로 나누면 「다시 읽기 전에 상태를 잡아 두는가」를 검증하지 못한다(잡는 시점을 락 뒤로 옮겨도 통과한다).
     */
    private Buncheol refreshedOnLock(
        final BuncheolStatus statusBefore,
        final BuncheolStatus statusAfter,
        final long feeBefore,
        final long feeAfter) {
      AtomicReference<BuncheolStatus> status = new AtomicReference<>(statusBefore);
      AtomicLong fee = new AtomicLong(feeBefore);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.isC2c()).willReturn(true);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getStatus()).willAnswer(invocation -> status.get());
      lenient()
          .when(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF))
          .thenAnswer(invocation -> fee.get());
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID))
          .willAnswer(
              invocation -> {
                status.set(statusAfter);
                fee.set(feeAfter);
                return buncheol;
              });
      given(buncheolMemberDomainService.getBuncheolMemberForShare(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      return buncheol;
    }

    // 🔴 개최자가 신청자 0명일 때 배송비를 바꾼 직후 첫 신청이 들어온 경우. 락 이전 값을 쓰면 옛 배송비가 묶음에 새겨지고
    // updatable=false 라 되돌릴 수 없다. 다시 읽기를 빼면(옛 잠금 조회로 되돌리면) 이 테스트가 깨진다.
    @Test
    void 락_뒤_다시_채운_배송비로_새_묶음을_연다() {
      Buncheol buncheol =
          refreshedOnLock(
              BuncheolStatus.RECRUITING, BuncheolStatus.RECRUITING, SHIPPING_FEE, CHANGED_SHIPPING_FEE);
      given(participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.empty());
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      givenParticipantAccount();
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(participationBundleDomainService)
          .should()
          .attach(
              any(),
              isNull(),
              eq(SHIPPING_ADDRESS_ID),
              eq(CHANGED_SHIPPING_FEE),
              any(),
              isNull(),
              eq(false),
              eq(NOW));
    }

    // 락을 기다리는 사이 성사 확정됐다 — 이전 값으로 진행했어도 조건부 INSERT 가 막았던 경우라 결과(BCH-060)가 같다.
    // 🔴 락 이전 상태를 다시 읽기 <b>뒤에</b> 잡으면(같은 인스턴스라 이미 덮인 값) 비교가 무력해져 이 테스트가 깨진다.
    @Test
    void 락을_기다리는_사이_상태가_바뀌면_신청을_받지_않는다() {
      refreshedOnLock(
          BuncheolStatus.RECRUITING, BuncheolStatus.PAYMENT_COLLECTING, SHIPPING_FEE, SHIPPING_FEE);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    // 🔴 다중 신청은 모집중 전용이다(합산 응답이 추가 모집에서는 거짓). 그 가드는 락 이전 값으로 판정하므로, 락 뒤 최신 상태가
    // 입금 수집중이면 추가 모집 분기로 새지 않고 거절돼야 한다. 상태 비교를 지우거나 잡는 시점을 락 뒤로 옮기면 깨진다.
    @Test
    void 다중_신청은_락_사이_성사_확정되면_추가_모집으로_새지_않는다() {
      refreshedOnLock(
          BuncheolStatus.RECRUITING, BuncheolStatus.PAYMENT_COLLECTING, SHIPPING_FEE, SHIPPING_FEE);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID,
                      PARTICIPANT_ID,
                      new ParticipateRequest(
                          null,
                          List.of(BUNCHEOL_MEMBER_ID, SECOND_MEMBER_ID),
                          SHIPPING_ADDRESS_ID,
                          null,
                          null)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      then(participationBundleDomainService)
          .should(never())
          .attach(any(), any(), any(), anyLong(), any(), any(), anyBoolean(), any());
    }
  }

  @Nested
  @DisplayName("진행 확정 뒤 추가 신청 테스트")
  class ExtraApplyTest {

    private static final Long PENDING_BUNDLE_ID = 800L;

    /** 진행 확정 회원 개최. 원래 마감은 하루 전에 지났다. 판정은 목이 아니라 도메인 코드로 태운다. */
    private Buncheol confirmedC2c(final BuncheolStatus status, final boolean extraRecruitOpen) {
      Buncheol buncheol = newInstance(Buncheol.class);
      setField(buncheol, "id", BUNCHEOL_ID);
      setField(buncheol, "hostId", HOST_ID);
      setField(buncheol, "flowType", FlowType.C2C);
      setField(buncheol, "status", status);
      setField(buncheol, "deadline", NOW.minus(Duration.ofDays(1)));
      setField(buncheol, "minHeadcount", 2);
      setField(buncheol, "shippingFeePolicy", ShippingFeePolicy.of((int) SHIPPING_FEE, null));
      setField(buncheol, "extraRecruitOpen", extraRecruitOpen);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      // 다중 신청 사전 가드에서 막히는 경우는 잠금까지 가지 않는다.
      lenient()
          .when(buncheolDomainService.getLatestBuncheolForUpdate(BUNCHEOL_ID))
          .thenReturn(buncheol);
      return buncheol;
    }

    private void givenSlot(final Long slotId, final long price) {
      given(buncheolMemberDomainService.getBuncheolMemberForShare(slotId, BUNCHEOL_ID))
          .willReturn(buncheolMember(slotId, price));
    }

    private void givenNewBundle(final Buncheol buncheol) {
      given(participationShippingAddressResolver.resolve(PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      givenParticipantAccount();
    }

    private void givenExtraInsert(final Long... ids) {
      var stub = given(participationDomainService.createParticipationIfExtraRecruiting(any()));
      for (Long id : ids) {
        stub =
            stub.willAnswer(
                invocation -> {
                  setField(invocation.getArgument(0), "id", id);
                  return true;
                });
      }
    }

    private Participation pendingExtra() {
      Participation pending = newInstance(Participation.class);
      setField(pending, "id", 450L);
      setField(pending, "status", ParticipationStatus.APPLIED);
      setField(pending, "bundleId", PENDING_BUNDLE_ID);
      return pending;
    }

    @Test
    void 추가_모집_중이면_원래_마감이_지났어도_신청으로_받고_계좌와_기한_없이_응답한다() {
      Buncheol buncheol = confirmedC2c(BuncheolStatus.CONFIRMED, true);
      given(buncheolDomainService.isEnded(BUNCHEOL_ID)).willReturn(false);
      givenSlot(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);
      given(participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.empty());
      givenNewBundle(buncheol);
      givenExtraInsert(PARTICIPATION_ID);

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      assertThat(result.participationIds()).containsExactly(PARTICIPATION_ID);
      assertThat(result.totalAmount()).isEqualTo(MEMBER_PRICE + SHIPPING_FEE);
      assertThat(result.dueAt()).isNull();
      assertThat(result.hostAccount()).isNull();
      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
      then(paymentStarter).shouldHaveNoInteractions();
    }

    // 원래 참여(입금 확인)는 배송이 이미 생겨 상속 원본이 아니다 — 새 묶음, 새 배송비, 추가 표시.
    @Test
    void 수락_전_추가_신청이_없으면_새_추가_묶음을_열고_배송비를_받는다() {
      Buncheol buncheol = confirmedC2c(BuncheolStatus.CONFIRMED, true);
      given(buncheolDomainService.isEnded(BUNCHEOL_ID)).willReturn(false);
      givenSlot(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);
      given(participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.empty());
      givenNewBundle(buncheol);
      givenExtraInsert(PARTICIPATION_ID);

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(participationBundleDomainService)
          .should()
          .attach(
              any(),
              isNull(),
              eq(SHIPPING_ADDRESS_ID),
              eq(SHIPPING_FEE),
              eq(REFUND_ACCOUNT_SNAPSHOT),
              isNull(),
              eq(true),
              eq(NOW));
    }

    @Test
    void 수락_전_추가_신청이_있으면_그_묶음에_붙고_배송비는_0원이다() {
      Buncheol buncheol = confirmedC2c(BuncheolStatus.CONFIRMED, true);
      given(buncheolDomainService.isEnded(BUNCHEOL_ID)).willReturn(false);
      givenSlot(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);
      Participation pending = pendingExtra();
      given(participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.of(pending));
      given(participationBundleDomainService.requireInheritedShippingAddressId(pending))
          .willReturn(SHIPPING_ADDRESS_ID);
      givenExtraInsert(PARTICIPATION_ID);

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      assertThat(result.totalAmount()).isEqualTo(MEMBER_PRICE);
      then(participationBundleDomainService)
          .should()
          .attach(
              any(),
              eq(PENDING_BUNDLE_ID),
              eq(SHIPPING_ADDRESS_ID),
              eq(0L),
              isNull(),
              isNull(),
              eq(true),
              eq(NOW));
    }

    @Test
    void 개최자_알림은_요청마다_한_번이고_이번_요청의_참여만_싣는다() {
      Buncheol buncheol = confirmedC2c(BuncheolStatus.CONFIRMED, true);
      given(buncheolDomainService.isEnded(BUNCHEOL_ID)).willReturn(false);
      givenSlot(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);
      givenSlot(SECOND_MEMBER_ID, SECOND_MEMBER_PRICE);
      Participation pending = pendingExtra();
      // 두 슬롯 모두 수락 전 추가 신청(450)에 붙는다. 그 참여는 이번 요청이 만든 것이 아니라 알림에 빠져야 한다.
      given(participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.of(pending));
      given(participationBundleDomainService.requireInheritedShippingAddressId(pending))
          .willReturn(SHIPPING_ADDRESS_ID);
      givenExtraInsert(PARTICIPATION_ID, PARTICIPATION_ID + 1);

      participationService.participate(
          BUNCHEOL_ID,
          PARTICIPANT_ID,
          new ParticipateRequest(
              null, List.of(BUNCHEOL_MEMBER_ID, SECOND_MEMBER_ID), SHIPPING_ADDRESS_ID, null, null));

      ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
      then(eventPublisher).should(atLeastOnce()).publishEvent(events.capture());
      assertThat(events.getAllValues())
          .filteredOn(ExtraAppliedEvent.class::isInstance)
          .containsExactly(
              new ExtraAppliedEvent(BUNCHEOL_ID, List.of(PARTICIPATION_ID, PARTICIPATION_ID + 1)));
      then(paymentStarter).shouldHaveNoInteractions();
    }

    @Test
    void 모집중_신청에는_개최자_알림을_내지_않는다() {
      Buncheol buncheol = confirmedC2c(BuncheolStatus.RECRUITING, false);
      setField(buncheol, "deadline", NOW.plus(Duration.ofDays(1)));
      givenSlot(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);
      given(participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.empty());
      givenNewBundle(buncheol);
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willAnswer(
              invocation -> {
                setField(invocation.getArgument(0), "id", PARTICIPATION_ID);
                return true;
              });

      participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      then(eventPublisher).should(never()).publishEvent(any(ExtraAppliedEvent.class));
      then(buncheolDomainService).should(never()).isEnded(any());
    }

    @Test
    void 스위치가_꺼진_진행확정_분철은_받지_않고_종료_판정도_하지_않는다() {
      confirmedC2c(BuncheolStatus.CONFIRMED, false);
      givenSlot(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
      then(buncheolDomainService).should(never()).isEnded(any());
      then(participationDomainService).should(never()).createParticipationIfExtraRecruiting(any());
    }

    @Test
    void 종료된_분철은_스위치가_켜져_있어도_받지_않는다() {
      confirmedC2c(BuncheolStatus.CONFIRMED, true);
      given(buncheolDomainService.isEnded(BUNCHEOL_ID)).willReturn(true);
      givenSlot(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
      then(participationDomainService).should(never()).createParticipationIfExtraRecruiting(any());
    }

    @Test
    void 입금_수집중이면_받지_않는다() {
      confirmedC2c(BuncheolStatus.PAYMENT_COLLECTING, true);
      givenSlot(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // 잠금을 기다리는 사이 스위치가 꺼졌으면 조건부 INSERT 가 0행이다(이중 방어).
    @Test
    void 조건부_INSERT_가_0행이면_받지_않는다() {
      Buncheol buncheol = confirmedC2c(BuncheolStatus.CONFIRMED, true);
      given(buncheolDomainService.isEnded(BUNCHEOL_ID)).willReturn(false);
      givenSlot(BUNCHEOL_MEMBER_ID, MEMBER_PRICE);
      given(participationDomainService.findInheritanceSource(buncheol, PARTICIPANT_ID))
          .willReturn(Optional.empty());
      givenNewBundle(buncheol);
      given(participationDomainService.createParticipationIfExtraRecruiting(any()))
          .willReturn(false);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID, PARTICIPANT_ID, participateRequest()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
      then(participationBundleDomainService)
          .should(never())
          .attach(any(), any(), any(), anyLong(), any(), any(), anyBoolean(), any());
    }

    @Test
    void 다중_신청_사전_가드는_스위치가_꺼진_진행확정을_막는다() {
      confirmedC2c(BuncheolStatus.CONFIRMED, false);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID,
                      PARTICIPANT_ID,
                      new ParticipateRequest(
                          null,
                          List.of(BUNCHEOL_MEMBER_ID, SECOND_MEMBER_ID),
                          SHIPPING_ADDRESS_ID,
                          null,
                          null)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);
      then(buncheolDomainService).should(never()).getLatestBuncheolForUpdate(any());
    }
  }
}
