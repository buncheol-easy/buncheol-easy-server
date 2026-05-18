package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolParticipationService {

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ShippingAddressDomainService shippingAddressDomainService;
  private final Clock clock;

  public Participation createParticipation(
      final Long buncheolId, final Long participantId, final ParticipateRequest request) {
    // 분철 조회 & 참여 가능한 분철인지 확인
    Buncheol buncheol = validateRecruitingBuncheol(buncheolId, participantId);
    BuncheolMember buncheolMember =
        buncheolMemberDomainService.getBuncheolMember(request.buncheolMemberId(), buncheolId);
    // 선택한 배송지 조회 & 해당 분철에서 지원하는 배송 방법인지 확인
    ShippingAddress shippingAddress =
        getAndValidateShippingAddress(participantId, buncheol, request.shippingAddressId());

    // 유저가 이미 같은 분철 멤버에 대해 참여중인지 확인
    validateNoActiveParticipation(buncheolMember.getId(), participantId);

    // 제시 금액이 멤버의 최소 금액 이상인지 확인
    buncheolMember.validateBidAmount(request.bidAmount());

    Participation participation =
        Participation.create(
            buncheolId,
            buncheolMember.getId(),
            participantId,
            shippingAddress.getId(),
            request.bidAmount());
    // 저장 시점에도 분철이 모집중인지 검사하여 원자적으로 INSERT
    boolean created = participationDomainService.createParticipationIfRecruiting(participation);
    if (!created) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
    return participation;
  }

  private Buncheol validateRecruitingBuncheol(final Long buncheolId, final Long participantId) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateRecruiting(Instant.now(clock));
    if (buncheol.isHost(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);
    }
    return buncheol;
  }

  private ShippingAddress getAndValidateShippingAddress(
      final Long participantId, final Buncheol buncheol, final Long shippingAddressId) {
    ShippingAddress shippingAddress =
        shippingAddressDomainService.getShippingAddress(shippingAddressId);
    if (!shippingAddress.isOwnedBy(participantId)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
    }
    buncheol.validateShippingMethodSupported(shippingAddress.getShippingMethod());
    return shippingAddress;
  }

  private void validateNoActiveParticipation(
      final Long buncheolMemberId, final Long participantId) {
    if (participationDomainService
        .findActiveParticipation(buncheolMemberId, participantId)
        .isPresent()) {
      throw new BusinessException(ErrorCode.PARTICIPATION_ALREADY_EXISTS);
    }
  }
}
