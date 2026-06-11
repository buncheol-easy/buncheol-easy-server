package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.ParticipationPaymentDetailResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationPaymentQueryService 단위 테스트")
class ParticipationPaymentQueryServiceTest {

  @InjectMocks private ParticipationPaymentQueryService participationPaymentQueryService;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private ShippingAddressDomainService shippingAddressDomainService;
  @Mock private UserDomainService userDomainService;
  @Mock private Buncheol buncheol;

  private static final Long BUNCHEOL_ID = 1L;
  private static final Long HOST_ID = 7L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long PARTICIPATION_ID = 50L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;
  private static final long BID_AMOUNT = 30_000L;
  private static final long SHIPPING_FEE = 3_000L;
  private static final Instant NOW = Instant.parse("2026-03-11T12:00:00Z");
  private static final Instant DUE = Instant.parse("2026-03-12T12:00:00Z");

  private Participation awaitingPaymentParticipation() {
    Participation participation =
        Participation.create(
            BUNCHEOL_ID, 20L, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, BID_AMOUNT);
    setId(participation, PARTICIPATION_ID);
    participation.awardAsWinner(DUE); // AWAITING_PAYMENT
    return participation;
  }

  private ShippingAddress shippingAddress() {
    return new ShippingAddress(
        SHIPPING_ADDRESS_ID, PARTICIPANT_ID, ShippingMethod.GS25_HALF, "GS25 강남점", null, false);
  }

  private User hostWithAccount() {
    User host = User.create("KAKAO", "kakao-host", "host@test.com");
    host.updateBankAccount("국민은행", "12345678", "홍길동");
    return host;
  }

  private void givenAmountLookups(final Participation participation) {
    given(participationDomainService.getParticipation(PARTICIPATION_ID)).willReturn(participation);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
    given(shippingAddressDomainService.getShippingAddress(SHIPPING_ADDRESS_ID))
        .willReturn(shippingAddress());
    given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(SHIPPING_FEE);
  }

  @Nested
  @DisplayName("결제 상세 조회 테스트")
  class GetPaymentDetailTest {

    @Test
    void AWAITING_PAYMENT_이면_금액과_개최자_계좌를_함께_반환한다() {
      Participation participation = awaitingPaymentParticipation();
      givenAmountLookups(participation);
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(userDomainService.getUser(HOST_ID)).willReturn(hostWithAccount());

      ParticipationPaymentDetailResponse response =
          participationPaymentQueryService.getPaymentDetail(PARTICIPANT_ID, PARTICIPATION_ID);

      assertThat(response.participationId()).isEqualTo(PARTICIPATION_ID);
      assertThat(response.paymentStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(response.bidAmount()).isEqualTo(BID_AMOUNT);
      assertThat(response.shippingFee()).isEqualTo(SHIPPING_FEE);
      assertThat(response.totalAmount()).isEqualTo(BID_AMOUNT + SHIPPING_FEE);
      assertThat(response.paymentDueAt()).isEqualTo(DUE);
      assertThat(response.hostAccount()).isNotNull();
      assertThat(response.hostAccount().bankName()).isEqualTo("국민은행");
      assertThat(response.hostAccount().accountNumber()).isEqualTo("12345678");
      assertThat(response.hostAccount().accountHolder()).isEqualTo("홍길동");
    }

    @Test
    void PAYMENT_REPORTED_이면_개최자_계좌를_노출한다() {
      Participation participation = awaitingPaymentParticipation();
      participation.reportPayment(NOW, SHIPPING_ADDRESS_ID); // PAYMENT_REPORTED
      givenAmountLookups(participation);
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(userDomainService.getUser(HOST_ID)).willReturn(hostWithAccount());

      ParticipationPaymentDetailResponse response =
          participationPaymentQueryService.getPaymentDetail(PARTICIPANT_ID, PARTICIPATION_ID);

      assertThat(response.paymentStatus()).isEqualTo(ParticipationStatus.PAYMENT_REPORTED);
      assertThat(response.hostAccount()).isNotNull();
    }

    @Test
    void CONFIRMED_이면_개최자_계좌를_노출하지_않는다() {
      Participation participation = awaitingPaymentParticipation();
      participation.reportPayment(NOW, SHIPPING_ADDRESS_ID);
      participation.confirmManualPayment(NOW); // CONFIRMED
      givenAmountLookups(participation);

      ParticipationPaymentDetailResponse response =
          participationPaymentQueryService.getPaymentDetail(PARTICIPANT_ID, PARTICIPATION_ID);

      assertThat(response.paymentStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
      assertThat(response.totalAmount()).isEqualTo(BID_AMOUNT + SHIPPING_FEE);
      assertThat(response.hostAccount()).isNull();
    }

    @Test
    void 본인이_아니면_권한_예외가_발생한다() {
      Long otherUserId = 999L;
      Participation participation = awaitingPaymentParticipation();
      given(participationDomainService.getParticipation(PARTICIPATION_ID)).willReturn(participation);

      assertThatThrownBy(
              () ->
                  participationPaymentQueryService.getPaymentDetail(
                      otherUserId, PARTICIPATION_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }
  }

  private void setId(final Participation participation, final Long id) {
    setFieldValue(participation, "id", id);
  }

  private void setFieldValue(final Object target, final String fieldName, final Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
