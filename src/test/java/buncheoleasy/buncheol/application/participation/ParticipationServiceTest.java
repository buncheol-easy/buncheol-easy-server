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
  @Mock private ApplicationEventPublisher eventPublisher;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Captor private ArgumentCaptor<Participation> participationCaptor;

  private ParticipateRequest participateRequest() {
    return new ParticipateRequest(
        BUNCHEOL_MEMBER_ID,
        SHIPPING_ADDRESS_ID,
        new RefundAccountRequest("국민", "12345678", "홍길동"));
  }

  private ShippingAddress shippingAddress() {
    return new ShippingAddress(
        SHIPPING_ADDRESS_ID, PARTICIPANT_ID, ShippingMethod.GS25_HALF, "GS25 강남역점", null, false);
  }

  private BuncheolMember buncheolMember() {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", BUNCHEOL_MEMBER_ID);
    setField(member, "buncheolId", BUNCHEOL_ID);
    setField(member, "memberId", 1001L);
    setField(member, "price", MEMBER_PRICE);
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
      assertThat(result.amount()).isEqualTo(MEMBER_PRICE + SHIPPING_FEE);
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
      assertThat(saved.getAmount()).isEqualTo(MEMBER_PRICE + SHIPPING_FEE);
      assertThat(saved.getDueAt()).isEqualTo(expectedDueAt);
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
    void 입금확인에_성공하면_입금확인_이벤트를_발행한다() {
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participationDomainService.getParticipation(PARTICIPATION_ID))
          .willReturn(participation);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

      participationService.confirmPayment(HOST_ID, PARTICIPATION_ID);

      then(buncheol).should().validateOwner(HOST_ID);
      then(participationDomainService).should().confirmPayment(PARTICIPATION_ID, NOW);
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
