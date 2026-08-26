package buncheoleasy.admin.application;

import buncheoleasy.admin.dto.request.AdminParticipationCodeIssueRequest;
import buncheoleasy.admin.dto.response.AdminBuncheolSlotResponse;
import buncheoleasy.admin.dto.response.AdminParticipationCodeResponse;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.code.ParticipationCode;
import buncheoleasy.buncheol.domain.code.ParticipationCodeDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.SlotAccessType;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 참여 코드 발급·폐기·조회. */
@Service
@RequiredArgsConstructor
public class AdminParticipationCodeService {

  private final BuncheolDomainService buncheolDomainService;
  private final BuncheolMemberDomainService buncheolMemberDomainService;
  private final ParticipationCodeDomainService participationCodeDomainService;
  private final ParticipationRepository participationRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final Clock clock;

  /** 발급 화면용 슬롯 목록 (등록 순). */
  @Transactional(readOnly = true)
  public List<AdminBuncheolSlotResponse> getSlots(final Long buncheolId) {
    buncheolDomainService.getBuncheol(buncheolId);
    List<BuncheolMember> members = buncheolMemberDomainService.findAllByBuncheolId(buncheolId);
    if (members.isEmpty()) {
      return List.of();
    }

    Map<Long, String> memberNames = resolveMemberNames(members);
    Set<Long> takenSlotIds =
        participationRepository.findActiveByBuncheolId(buncheolId).stream()
            .map(Participation::getBuncheolMemberId)
            .collect(Collectors.toSet());
    // 만료분이 겹칠 수 있어(만료는 발급을 막지 않는다) 중복 키로 터지지 않게 최신 1건만 남긴다.
    Map<Long, ParticipationCode> activeCodeBySlot =
        participationCodeDomainService.findAllByBuncheolId(buncheolId).stream()
            .filter(ParticipationCode::isOutstanding)
            .filter(code -> code.getBuncheolMemberId() != null)
            .collect(
                Collectors.toMap(
                    ParticipationCode::getBuncheolMemberId,
                    Function.identity(),
                    (latest, older) -> latest));

    final Instant now = Instant.now(clock);
    return members.stream()
        .sorted(Comparator.comparing(BuncheolMember::getId))
        .map(
            member -> {
              ParticipationCode activeCode = activeCodeBySlot.get(member.getId());
              return new AdminBuncheolSlotResponse(
                  member.getId(),
                  memberNames.get(member.getMemberId()),
                  member.getPrice(),
                  member.getAccessType(),
                  takenSlotIds.contains(member.getId()),
                  activeCode == null
                      ? null
                      : AdminParticipationCodeResponse.of(
                          activeCode, memberNames.get(member.getMemberId()), now));
            })
        .toList();
  }

  /** 발급 이력 전체 (최신순). 폐기·사용분도 포함한다 — 재발급 경위 추적이 목적이다. */
  @Transactional(readOnly = true)
  public List<AdminParticipationCodeResponse> getCodes(final Long buncheolId) {
    buncheolDomainService.getBuncheol(buncheolId);
    List<ParticipationCode> codes = participationCodeDomainService.findAllByBuncheolId(buncheolId);
    if (codes.isEmpty()) {
      return List.of();
    }
    Map<Long, String> memberNames =
        resolveMemberNames(buncheolMemberDomainService.findAllByBuncheolId(buncheolId));
    Map<Long, Long> groupMemberIdBySlot =
        buncheolMemberDomainService.findAllByBuncheolId(buncheolId).stream()
            .collect(Collectors.toMap(BuncheolMember::getId, BuncheolMember::getMemberId));

    final Instant now = Instant.now(clock);
    return codes.stream()
        .map(
            code ->
                AdminParticipationCodeResponse.of(
                    code, memberNames.get(groupMemberIdBySlot.get(code.getBuncheolMemberId())), now))
        .toList();
  }

  /** 재발급은 폐기와 발급이 한 트랜잭션이어야 한다. */
  @Transactional
  public AdminParticipationCodeResponse issue(
      final Long buncheolId, final AdminParticipationCodeIssueRequest request) {
    final Instant now = Instant.now(clock);
    buncheolDomainService.getBuncheol(buncheolId);
    BuncheolMember member =
        buncheolMemberDomainService.getBuncheolMember(request.buncheolMemberId(), buncheolId);

    // 이미 점유된 슬롯에 발급하면 코드를 받은 사람이 참여 시점에 BCH-070 으로 막힌다 — 헛 코드를 보내기 전에 끊는다.
    if (isSlotTaken(buncheolId, member.getId())) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_SLOT_TAKEN);
    }

    Instant expiresAt = now.plus(validity(request.validHours()));
    ParticipationCode code =
        request.reissue()
            ? participationCodeDomainService.reissue(member, request.issuedTo(), expiresAt, now)
            : participationCodeDomainService.issue(member, request.issuedTo(), expiresAt, now);

    return AdminParticipationCodeResponse.of(
        code, resolveMemberNames(List.of(member)).get(member.getMemberId()), now);
  }

  /**
   * 슬롯 접근 정책 전환. 개최 폼은 전 슬롯을 선착순으로 만들고 배정 지정은 여기서 한다 — 일반 유저 개최 화면에 운영 전용 옵션을
   * 노출하지 않기 위함이다.
   */
  @Transactional
  public void changeSlotAccessType(
      final Long buncheolId, final Long buncheolMemberId, final SlotAccessType accessType) {
    Buncheol buncheol = buncheolDomainService.getBuncheol(buncheolId);
    if (accessType == SlotAccessType.CODE_ONLY) {
      if (buncheol.isC2c()) {
        throw new BusinessException(ErrorCode.PARTICIPATION_CODE_SLOT_NOT_CODE_ONLY);
      }
      if (!buncheolMemberDomainService.getBuncheolMember(buncheolMemberId, buncheolId).isFree()) {
        throw new BusinessException(ErrorCode.PARTICIPATION_CODE_SLOT_NOT_FREE);
      }
    }
    buncheolMemberDomainService.changeAccessType(buncheolMemberId, buncheolId, accessType);
  }

  /** 코드 폐기 (유출 신고 등). 이미 사용된 코드는 폐기할 수 없다. */
  @Transactional
  public void revoke(final Long codeId) {
    participationCodeDomainService.revoke(codeId, Instant.now(clock));
  }

  private boolean isSlotTaken(final Long buncheolId, final Long buncheolMemberId) {
    return participationRepository.findActiveByBuncheolId(buncheolId).stream()
        .anyMatch(participation -> buncheolMemberId.equals(participation.getBuncheolMemberId()));
  }

  private Duration validity(final Integer validHours) {
    return validHours == null
        ? ParticipationCodeDomainService.DEFAULT_VALIDITY
        : Duration.ofHours(validHours);
  }

  private Map<Long, String> resolveMemberNames(final List<BuncheolMember> members) {
    List<Long> groupMemberIds =
        members.stream().map(BuncheolMember::getMemberId).distinct().toList();
    return groupMemberRepository.findAllByIds(groupMemberIds).stream()
        .collect(Collectors.toMap(GroupMember::getId, GroupMember::getName));
  }
}
