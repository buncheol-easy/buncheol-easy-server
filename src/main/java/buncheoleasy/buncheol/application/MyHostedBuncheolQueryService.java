package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolHostCancellability;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.ConfirmCheck;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.BuncheolAppliedBundleCount;
import buncheoleasy.buncheol.domain.participation.BuncheolConfirmedParticipationCount;
import buncheoleasy.buncheol.domain.participation.BuncheolUnconfirmedParticipationCount;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyHostedBuncheolQueryService {

  private final BuncheolRepository buncheolRepository;
  private final BuncheolMemberRepository buncheolMemberRepository;
  private final ParticipationRepository participationRepository;
  private final GroupRepository groupRepository;
  private final BuncheolImageRepository buncheolImageRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<MyHostedBuncheolResponse> getMyHostedBuncheols(final Long hostId) {
    List<Buncheol> buncheols = buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(hostId);
    if (buncheols.isEmpty()) {
      return List.of();
    }

    List<Long> buncheolIds = buncheols.stream().map(Buncheol::getId).toList();
    List<Long> groupIds = buncheols.stream().map(Buncheol::getGroupId).distinct().toList();

    // 닫힌 자리는 신청을 받지 않으므로 세지 않는다 — removeMember 의 열린 자리 수(BuncheolService)와 같은 수.
    // CODE_ONLY 도 자리라 부정형으로 거른다(`= OPEN` 으로 쓰면 LEGACY 코드 자리가 빠진다).
    Map<Long, Long> slotCountByBuncheolId =
        buncheolMemberRepository.findAllByBuncheolIds(buncheolIds).stream()
            .filter(bm -> !bm.isClosed())
            .collect(Collectors.groupingBy(BuncheolMember::getBuncheolId, Collectors.counting()));

    Map<Long, Long> activeCountByBuncheolId =
        participationRepository.countActiveByBuncheolIds(buncheolIds).stream()
            .collect(
                Collectors.toMap(
                    BuncheolActiveParticipationCount::buncheolId,
                    BuncheolActiveParticipationCount::count));

    // 진행 확정 판정용 확인·미확인 건수. 카드마다 조회하지 않고 입금 수집중 분철만 골라 한 번에 센다 — 없으면 어댑터가 쿼리를 건너뛴다.
    List<Long> collectingIds =
        buncheols.stream()
            .filter(b -> b.getStatus() == BuncheolStatus.PAYMENT_COLLECTING)
            .map(Buncheol::getId)
            .toList();
    Map<Long, Long> confirmedCountByBuncheolId =
        participationRepository.countConfirmedByBuncheolIds(collectingIds).stream()
            .collect(
                Collectors.toMap(
                    BuncheolConfirmedParticipationCount::buncheolId,
                    BuncheolConfirmedParticipationCount::count));
    Map<Long, Long> unconfirmedCountByBuncheolId =
        participationRepository.countUnconfirmedByBuncheolIds(collectingIds).stream()
            .collect(
                Collectors.toMap(
                    BuncheolUnconfirmedParticipationCount::buncheolId,
                    BuncheolUnconfirmedParticipationCount::count));
    // 「수락해 주세요」 배지용 수락 전 추가 신청 묶음 수. 진행 확정 분철만 모아 한 번에 센다(없으면 쿼리 없음).
    Map<Long, Long> pendingAcceptCountByBuncheolId =
        participationRepository
            .countAppliedBundlesByBuncheolIds(
                buncheols.stream()
                    .filter(b -> b.getStatus() == BuncheolStatus.CONFIRMED)
                    .map(Buncheol::getId)
                    .toList())
            .stream()
            .collect(
                Collectors.toMap(
                    BuncheolAppliedBundleCount::buncheolId, BuncheolAppliedBundleCount::count));
    final Instant now = Instant.now(clock);

    // 「종료」 판정도 같은 방식으로 한 번에 묻는다. 끝날 수 있는 건 진행확정뿐이라 그 id 만 넘기고, 없으면 어댑터가
    // 쿼리를 건너뛴다. 판정식은 회원탈퇴 가드와 공유한다 (BuncheolRepository#findEndedIds).
    List<Long> endedCheckTargetIds =
        buncheols.stream()
            .filter(b -> b.getStatus() == BuncheolStatus.CONFIRMED)
            .map(Buncheol::getId)
            .toList();
    Set<Long> endedIds = buncheolRepository.findEndedIds(endedCheckTargetIds);

    Map<Long, String> groupNameById =
        groupRepository.findAllByIds(groupIds).stream()
            .collect(Collectors.toMap(Group::getId, Group::getName));

    Map<Long, String> thumbnailByBuncheolId =
        buncheolImageRepository.findThumbnailsByBuncheolIds(buncheolIds).stream()
            .collect(Collectors.toMap(BuncheolImage::getBuncheolId, BuncheolImage::getImageUrl));

    return buncheols.stream()
        .map(
            b ->
                toResponse(
                    b,
                    slotCountByBuncheolId,
                    activeCountByBuncheolId,
                    confirmedCountByBuncheolId,
                    unconfirmedCountByBuncheolId,
                    pendingAcceptCountByBuncheolId,
                    now,
                    endedIds,
                    groupNameById,
                    thumbnailByBuncheolId))
        .toList();
  }

  private MyHostedBuncheolResponse toResponse(
      final Buncheol buncheol,
      final Map<Long, Long> slotCountByBuncheolId,
      final Map<Long, Long> activeCountByBuncheolId,
      final Map<Long, Long> confirmedCountByBuncheolId,
      final Map<Long, Long> unconfirmedCountByBuncheolId,
      final Map<Long, Long> pendingAcceptCountByBuncheolId,
      final Instant now,
      final Set<Long> endedIds,
      final Map<Long, String> groupNameById,
      final Map<Long, String> thumbnailByBuncheolId) {
    int slotCount = slotCountByBuncheolId.getOrDefault(buncheol.getId(), 0L).intValue();
    long activeCount = activeCountByBuncheolId.getOrDefault(buncheol.getId(), 0L);
    long confirmedCount = confirmedCountByBuncheolId.getOrDefault(buncheol.getId(), 0L);
    long unconfirmedCount = unconfirmedCountByBuncheolId.getOrDefault(buncheol.getId(), 0L);
    return new MyHostedBuncheolResponse(
        buncheol.getId(),
        buncheol.getTitle(),
        groupNameById.get(buncheol.getGroupId()),
        buncheol.getStatus(),
        buncheol.getDeadline(),
        slotCount,
        activeCount,
        buncheol.getCreatedAt(),
        thumbnailByBuncheolId.get(buncheol.getId()),
        buncheol.getFlowType(),
        // 취소 API 게이트와 같은 판정을 그대로 내린다 — 카드가 상태로 재판정하면 서버와 갈린다 (docs/56 S-2).
        BuncheolHostCancellability.of(buncheol.getStatus()),
        endedIds.contains(buncheol.getId()),
        // 관리 화면·진행 확정 게이트와 같은 판정. 미확인 수도 게이트와 같은 집합(입금 대기·보냈어요)으로 센다.
        ConfirmCheck.of(
            buncheol.getStatus(),
            confirmedCount,
            unconfirmedCount,
            buncheol.getPaymentDueAt(),
            now),
        buncheol.isExtraRecruitOpen(),
        pendingAcceptCountByBuncheolId.getOrDefault(buncheol.getId(), 0L));
  }
}
