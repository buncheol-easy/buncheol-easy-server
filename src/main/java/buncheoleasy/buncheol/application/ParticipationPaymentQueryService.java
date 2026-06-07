package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.ParticipationPaymentDetailResponse;
import buncheoleasy.buncheol.dto.response.ParticipationPaymentDetailResponse.HostAccountResponse;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 낙찰자(구매자) 본인의 결제 상세(금액·기한·개최자 계좌) 조회. 계좌번호가 포함되므로 로그에 남기지 않는다. */
@Service
@RequiredArgsConstructor
public class ParticipationPaymentQueryService {

  private final ParticipationDomainService participationDomainService;
  private final BuncheolDomainService buncheolDomainService;
  private final ShippingAddressDomainService shippingAddressDomainService;
  private final UserDomainService userDomainService;

  @Transactional(readOnly = true)
  public ParticipationPaymentDetailResponse getPaymentDetail(
      final Long participantId, final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId); // 본인 아니면 PARTICIPATION_NO_PERMISSION (403)

    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    ShippingAddress shippingAddress =
        shippingAddressDomainService.getShippingAddress(participation.getShippingAddressId());

    long bidAmount = participation.getBidAmount();
    long shippingFee = buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
    long totalAmount = bidAmount + shippingFee;

    return new ParticipationPaymentDetailResponse(
        participation.getId(),
        participation.getStatus(),
        bidAmount,
        shippingFee,
        totalAmount,
        participation.getDueAt(),
        resolveHostAccount(participation.getStatus(), buncheol));
  }

  // 입금 대기(AWAITING_PAYMENT)/신고(PAYMENT_REPORTED) 단계에서만 개최자 계좌를 노출한다. 그 외 상태에선 null.
  private HostAccountResponse resolveHostAccount(
      final ParticipationStatus status, final Buncheol buncheol) {
    if (!isAccountVisible(status)) {
      return null;
    }
    User host = userDomainService.getUser(buncheol.getHostId());
    BankAccount bankAccount = host.getBankAccount();
    if (bankAccount == null) {
      return null;
    }
    return HostAccountResponse.from(bankAccount);
  }

  private boolean isAccountVisible(final ParticipationStatus status) {
    return status == ParticipationStatus.AWAITING_PAYMENT
        || status == ParticipationStatus.PAYMENT_REPORTED;
  }
}
