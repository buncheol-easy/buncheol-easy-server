package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationType;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolParticipationService {

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ShippingAddressDomainService shippingAddressDomainService;

  public Participation createParticipation(
      final Long buncheolId, final Long participantId, final ParticipateRequest request) {
    // 분철 조회 & 참여 가능한 분철인지 확인
    final Buncheol buncheol = validateRecruitingBuncheol(buncheolId, participantId);
    final BuncheolMember buncheolMember =
        buncheolMemberDomainService.getBuncheolMember(request.buncheolMemberId(), buncheolId);
    // 선택한 배송지 조회 & 해당 분철에서 지원하는 배송 방법인지 확인
    final ShippingAddress shippingAddress =
        getAndValidateShippingAddress(participantId, buncheol, request.shippingAddressId());

    // 유저가 이미 같은 분철 멤버에 대해 참여중인지 확인
    validateNoActiveParticipation(buncheolMember.getId(), participantId);

    // 즉시구매로 이미 품절된 멤버 슬롯인지 확인
    if (participationDomainService.isInstantSlotTaken(buncheolMember.getId())) {
      throw new BusinessException(ErrorCode.PARTICIPATION_MEMBER_ALREADY_TAKEN);
    }

    // 참여 타입에 따른 참여 객체 생성
    if (request.type() == ParticipationType.INSTANT) {
      return handleInstantParticipation(
          buncheolId,
          buncheolMember.getId(),
          participantId,
          shippingAddress.getId(),
          buncheolMember.getInstantPrice());
    }
    return handleBidParticipation(
        buncheolId, buncheolMember, participantId, shippingAddress.getId(), request.bidAmount());
  }

  private Buncheol validateRecruitingBuncheol(final Long buncheolId, final Long participantId) {
    final Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateRecruiting();
    if (buncheol.isHost(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);
    }
    return buncheol;
  }

  private ShippingAddress getAndValidateShippingAddress(
      final Long participantId, final Buncheol buncheol, final Long shippingAddressId) {
    final ShippingAddress shippingAddress =
        shippingAddressDomainService.getShippingAddress(shippingAddressId);
    if (!shippingAddress.isOwnedBy(participantId)) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
    }
    buncheol.validateShippingMethodSupported(shippingAddress.getShippingMethod());
    return shippingAddress;
  }

  private Participation handleInstantParticipation(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final long instantPriceSnapshot) {
    final Participation participation =
        Participation.createInstant(
            buncheolId, buncheolMemberId, participantId, shippingAddressId, instantPriceSnapshot);
    // 즉시구매 참여 객체를 저장하는 시점에도 해당 분철&멤버에 참여할 수 있는 상황인지 검사하여 저장함.
    final boolean created =
        participationDomainService.createInstantParticipationIfRecruiting(participation);
    if (!created) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
    return participation;
  }

  private Participation handleBidParticipation(
      final Long buncheolId,
      final BuncheolMember buncheolMember,
      final Long participantId,
      final Long shippingAddressId,
      final long bidAmount) {
    // 제시 가능한지 확인
    buncheolMember.validateBidAllowed();
    buncheolMember.validateBidAmount(bidAmount);

    final Participation participation =
        Participation.createBid(
            buncheolId, buncheolMember.getId(), participantId, shippingAddressId, bidAmount);
    participationDomainService.createParticipation(participation);
    return participation;
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
