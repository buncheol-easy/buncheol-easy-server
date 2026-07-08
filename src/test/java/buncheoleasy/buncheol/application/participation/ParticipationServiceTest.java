package buncheoleasy.buncheol.application.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import buncheoleasy.buncheol.application.BuncheolConfirmedFinalizer;
import buncheoleasy.buncheol.application.DeliverySnapshotCreator;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.buncheol.dto.request.RefundAccountRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

  @InjectMocks private ParticipationService participationService;

  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationShippingAddressResolver participationShippingAddressResolver;
  @Mock private UserDomainService userDomainService;
  @Mock private DeliverySnapshotCreator deliverySnapshotCreator;
  @Mock private BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Captor private ArgumentCaptor<Participation> participationCaptor;

  private ParticipateRequest participateRequest() {
    return participateRequest(BUNCHEOL_MEMBER_ID);
  }

  private ParticipateRequest participateRequest(final Long... buncheolMemberIds) {
    return new ParticipateRequest(
        List.of(buncheolMemberIds),
        SHIPPING_ADDRESS_ID,
        new RefundAccountRequest("국민", "12345678", "홍길동"));
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
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any())).willReturn(true);
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      // 30분 창이 deadline 보다 빠르므로 dueAt = now + 30분.
      Instant expectedDueAt = NOW.plus(Duration.ofMinutes(30));
      assertThat(result.totalAmount()).isEqualTo(MEMBER_PRICE + SHIPPING_FEE);
      assertThat(result.dueAt()).isEqualTo(expectedDueAt);
      assertThat(result.hostAccount()).isEqualTo(HOST_ACCOUNT);

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
      assertThat(saved.getTotalAmount()).isEqualTo(MEMBER_PRICE + SHIPPING_FEE);
      assertThat(saved.getDueAt()).isEqualTo(expectedDueAt);
      then(eventPublisher).should().publishEvent(any(ParticipationCreatedEvent.class));
    }

    @Test
    void 여러_멤버를_한_번에_점유하면_배송비는_1회만_부과되고_각_참여가_생성된다() {
      Long secondMemberId = 102L;
      long secondPrice = 40_000L;
      Instant deadline = NOW.plus(Duration.ofDays(7));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(deadline);
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      given(buncheolMemberDomainService.getBuncheolMember(secondMemberId, BUNCHEOL_ID))
          .willReturn(buncheolMember(secondMemberId, secondPrice));
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any())).willReturn(true);
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);

      // 데드락 예방을 위해 슬롯 점유는 멤버 ID 오름차순으로 고정된다 — 요청을 역순(102, 101)으로 넣어도 101 부터 INSERT 된다.
      ParticipateResult result =
          participationService.participate(
              BUNCHEOL_ID, PARTICIPANT_ID, participateRequest(secondMemberId, BUNCHEOL_MEMBER_ID));

      // 총액 = 두 멤버 금액 합 + 배송비 1회.
      assertThat(result.totalAmount()).isEqualTo(MEMBER_PRICE + secondPrice + SHIPPING_FEE);

      then(participationDomainService)
          .should(times(2))
          .createParticipationIfRecruiting(participationCaptor.capture());
      List<Participation> saved = participationCaptor.getAllValues();
      // 멤버 ID 오름차순(101→102)으로 처리되고, 멤버 금액(amount)은 각 슬롯의 굿즈 가격,
      // 배송비(shippingFee)는 첫 슬롯(가장 작은 ID)에만 얹힌다.
      assertThat(saved.get(0).getBuncheolMemberId()).isEqualTo(BUNCHEOL_MEMBER_ID);
      assertThat(saved.get(0).getAmount()).isEqualTo(MEMBER_PRICE);
      assertThat(saved.get(0).getShippingFee()).isEqualTo(SHIPPING_FEE);
      assertThat(saved.get(1).getBuncheolMemberId()).isEqualTo(secondMemberId);
      assertThat(saved.get(1).getAmount()).isEqualTo(secondPrice);
      assertThat(saved.get(1).getShippingFee()).isZero();
    }

    @Test
    void 묶음_중_한_슬롯이라도_점유에_실패하면_예외가_발생한다() {
      Long secondMemberId = 102L;
      Instant deadline = NOW.plus(Duration.ofDays(7));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(deadline);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      given(buncheolMemberDomainService.getBuncheolMember(secondMemberId, BUNCHEOL_ID))
          .willReturn(buncheolMember(secondMemberId, 40_000L));
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      // 첫 슬롯은 성공, 둘째 슬롯은 모집 마감 → 전체 트랜잭션 롤백 의도.
      given(participationDomainService.createParticipationIfRecruiting(any()))
          .willReturn(true, false);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID,
                      PARTICIPANT_ID,
                      participateRequest(BUNCHEOL_MEMBER_ID, secondMemberId)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_RECRUITING);

      // 호스트 계좌 조회까지 가지 않고 둘째 슬롯에서 끊긴다.
      then(userDomainService).should(never()).getUser(anyLong());
    }

    @Test
    void 같은_멤버를_중복으로_선택하면_저장하지_않고_예외가_발생한다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);

      assertThatThrownBy(
              () ->
                  participationService.participate(
                      BUNCHEOL_ID,
                      PARTICIPANT_ID,
                      participateRequest(BUNCHEOL_MEMBER_ID, BUNCHEOL_MEMBER_ID)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_DUPLICATE_MEMBER);

      then(participationDomainService).should(never()).createParticipationIfRecruiting(any());
    }

    @Test
    void 마감이_30분_이내면_입금만료시각이_deadline으로_클램프된다() {
      Instant deadline = NOW.plus(Duration.ofMinutes(5));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheol.getDeadline()).willReturn(deadline);
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
      given(
              participationShippingAddressResolver.resolve(
                  PARTICIPANT_ID, buncheol, SHIPPING_ADDRESS_ID))
          .willReturn(shippingAddress());
      given(participationDomainService.createParticipationIfRecruiting(any())).willReturn(true);
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);

      ParticipateResult result =
          participationService.participate(BUNCHEOL_ID, PARTICIPANT_ID, participateRequest());

      // deadline(5분 후)이 30분 창보다 빠르므로 dueAt = deadline.
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
    void 배송지가_본인_소유가_아니면_저장하지_않고_예외가_전파된다() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      given(buncheol.isHost(PARTICIPANT_ID)).willReturn(false);
      given(buncheolMemberDomainService.getBuncheolMember(BUNCHEOL_MEMBER_ID, BUNCHEOL_ID))
          .willReturn(buncheolMember());
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

    @Test
    void 입금확인에_성공하면_배송_스냅샷을_만들고_입금확인_이벤트를_발행한다() {
      Participation participation = mock(Participation.class);
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
