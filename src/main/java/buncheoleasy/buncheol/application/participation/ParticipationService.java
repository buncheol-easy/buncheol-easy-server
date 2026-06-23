package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 참여 라이프사이클(참여 신청 / 개최자 입금확인 / 참여자 취소) 애플리케이션 서비스. */
@Service
@RequiredArgsConstructor
public class ParticipationService {

  // 참여(개최자 계좌 노출) 시점부터 입금 만료까지의 기본 창. 단, deadline 을 넘지 않도록 클램프한다.
  private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationDomainService participationDomainService;
  private final ParticipationShippingAddressResolver participationShippingAddressResolver;
  private final UserDomainService userDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 분철 참여(멤버 슬롯 선착순 점유). 점유에 성공하면 개최자 계좌가 노출되고 입금 만료 타이머(min(now+30분, deadline))가 시작된다. 참여와 동시에 환불
   * 계좌를 입력받는다.
   */
  @Transactional
  public ParticipateResult participate(
      final Long buncheolId, final Long participantId, final ParticipateRequest request) {
    final Instant now = Instant.now(clock);

    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    buncheol.validateRecruiting(now);
    if (buncheol.isHost(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_HOST_CANNOT_PARTICIPATE);
    }

    BuncheolMember member =
        buncheolMemberDomainService.getBuncheolMember(request.buncheolMemberId(), buncheolId);
    ShippingAddress shippingAddress =
        participationShippingAddressResolver.resolve(
            participantId, buncheol, request.shippingAddressId());

    long amount = member.getPrice() + buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
    Instant dueAt = paymentDueAt(now, buncheol.getDeadline());

    Participation participation =
        Participation.create(
            buncheolId,
            member.getId(),
            participantId,
            shippingAddress.getId(),
            amount,
            request.toRefundAccount(),
            dueAt);
    // 저장 시점에도 분철이 모집중인지 원자적으로 재확인(없으면 false). 멤버 슬롯이 이미 점유됐으면 DuplicateKey →
    // PARTICIPATION_ALREADY_EXISTS.
    if (!participationDomainService.createParticipationIfRecruiting(participation)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }

    // 개최자(MVP 운영자)는 관리 화면으로 직접 확인하므로 참여 발생 시 별도 알림을 보내지 않는다.
    BankAccount hostAccount = userDomainService.getUser(buncheol.getHostId()).getBankAccount();
    return new ParticipateResult(participation.getId(), amount, dueAt, hostAccount);
  }

  /** 개최자의 수동 입금확인 (AWAITING_PAYMENT → CONFIRMED). 입금 기한(30분 칼컷) 내에만 가능하다. */
  @Transactional
  public void confirmPayment(final Long hostId, final Long participationId) {
    Participation participation = participationDomainService.getParticipation(participationId);
    Buncheol buncheol = buncheolDomainService.getBuncheol(participation.getBuncheolId());
    buncheol.validateOwner(hostId);

    participationDomainService.confirmPayment(participationId, Instant.now(clock));
    eventPublisher.publishEvent(new PaymentConfirmedEvent(participationId));
  }

  private Instant paymentDueAt(final Instant now, final Instant deadline) {
    Instant windowEnd = now.plus(PAYMENT_WINDOW);
    return windowEnd.isBefore(deadline) ? windowEnd : deadline;
  }
}
