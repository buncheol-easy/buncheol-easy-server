package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationPaymentAmountResolver 단위 테스트")
class ParticipationPaymentAmountResolverTest {

  @InjectMocks private ParticipationPaymentAmountResolver participationPaymentAmountResolver;

  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private ShippingAddressDomainService shippingAddressDomainService;

  @Test
  void 결제_금액은_제시가에_선택한_배송수단의_배송비를_더한_값이다() {
    // 분철 1, 멤버 10, 참여자 100, 배송지 200, 제시가 50,000
    Participation participation = Participation.create(1L, 10L, 100L, 200L, 50_000L);
    Buncheol buncheol = mock(Buncheol.class);
    ShippingAddress shippingAddress =
        new ShippingAddress(200L, 100L, ShippingMethod.GS25_HALF, "GS25 강남점", null, false);
    given(buncheolDomainService.getBuncheol(1L)).willReturn(buncheol);
    given(shippingAddressDomainService.getShippingAddress(200L)).willReturn(shippingAddress);
    given(buncheol.shippingFeeFor(ShippingMethod.GS25_HALF)).willReturn(3_000);

    long amount = participationPaymentAmountResolver.resolve(participation);

    assertThat(amount).isEqualTo(53_000L);
  }
}
