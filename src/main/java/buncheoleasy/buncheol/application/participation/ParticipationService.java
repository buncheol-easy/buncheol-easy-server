package buncheoleasy.buncheol.application.participation;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.dto.request.ParticipateRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
   * 분철 참여(멤버 슬롯 선착순 점유). 한 요청으로 여러 멤버 슬롯을 동시에 점유할 수 있다. 점유에 성공하면 개최자 계좌가 노출되고 입금 만료
   * 타이머(min(now+30분, deadline))가 시작된다. 참여와 동시에 환불 계좌를 입력받는다.
   *
   * <p>각 참여는 멤버 금액(amount)과 배송비(shippingFee)를 분리 저장한다. 배송비는 묶음당 1회만 부과하므로 첫 슬롯에만 얹고 나머지는 0 이며, 응답
   * 총액(= 각 참여 getTotalAmount 의 합)에는 배송비가 한 번만 포함된다. 슬롯 중 하나라도 점유에 실패하면(이미 점유됨/모집 마감) 트랜잭션 전체가 롤백돼 전부
   * 성공하거나 전부 실패한다.
   *
   * <p>주의: "첫 슬롯에만 배송비" 모델은 묶음 단위 입금/취소(현재 MVP: 분철 전체 취소만 존재)를 전제로 한다. 슬롯 단위 부분 취소·부분 환불이 도입되면 배송비가
   * 붙은 슬롯만 취소될 때 환불 금액이 왜곡될 수 있으므로, 그 시점엔 배송비 귀속 규칙을 재검토해야 한다. 서로 다른 참여 호출은 각각 배송비를 부과하며, 합산 환불은
   * 별도 문의 통로로 수동 처리한다.
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

    List<Long> memberIds = request.buncheolMemberIds();
    validateNoDuplicateMembers(memberIds);
    List<BuncheolMember> members =
        memberIds.stream()
            .map(memberId -> buncheolMemberDomainService.getBuncheolMember(memberId, buncheolId))
            .toList();

    ShippingAddress shippingAddress =
        participationShippingAddressResolver.resolve(
            participantId, buncheol, request.shippingAddressId());
    long shippingFee = buncheol.shippingFeeFor(shippingAddress.getShippingMethod());
    Instant dueAt = paymentDueAt(now, buncheol.getDeadline());
    RefundAccount refundAccount = request.toRefundAccount();

    List<Long> participationIds = new ArrayList<>(members.size());
    long totalAmount = 0;
    for (int i = 0; i < members.size(); i++) {
      BuncheolMember member = members.get(i);
      // 멤버 금액과 배송비를 분리 저장한다. 배송비는 첫 슬롯에만 1회 부과하고 나머지는 0.
      long slotShippingFee = (i == 0) ? shippingFee : 0L;
      Participation participation =
          Participation.create(
              buncheolId,
              member.getId(),
              participantId,
              shippingAddress.getId(),
              member.getPrice(),
              slotShippingFee,
              refundAccount,
              dueAt);
      // 저장 시점에도 분철이 모집중인지 원자적으로 재확인(없으면 false → 전체 롤백). 멤버 슬롯이 이미 점유됐으면 DuplicateKey →
      // PARTICIPATION_ALREADY_EXISTS 로 전체 롤백.
      if (!participationDomainService.createParticipationIfRecruiting(participation)) {
        throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
      }
      participationIds.add(participation.getId());
      totalAmount += participation.getTotalAmount();
    }

    // 개최자(MVP 운영자)는 관리 화면으로 직접 확인하므로 참여 발생 시 별도 알림을 보내지 않는다.
    BankAccount hostAccount = userDomainService.getUser(buncheol.getHostId()).getBankAccount();
    return new ParticipateResult(participationIds, totalAmount, dueAt, hostAccount);
  }

  private void validateNoDuplicateMembers(final List<Long> memberIds) {
    if (Set.copyOf(memberIds).size() != memberIds.size()) {
      throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE_MEMBER);
    }
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
