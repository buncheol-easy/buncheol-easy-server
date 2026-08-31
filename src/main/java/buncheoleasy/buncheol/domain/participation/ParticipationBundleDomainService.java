package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 참여 묶음의 생성·연결·종료를 한곳에 모은다 (docs/70 §3 · docs/80 ④).
 *
 * <p>묶음은 <b>현실의 돈 단위</b>다 — 이체 1회 · 배송비 1회 · 택배 1개가 이 단위와 1:1로 맞는다. 값이 슬롯 행에 흩어져 있으면 그 행이
 * 취소될 때 값도 같이 죽기 때문에(docs/62 M-01) 묶음이 값을 소유한다.
 *
 * <p><b>이 클래스가 지키는 불변식은 하나다 — "묶음을 열었으면 반드시 연결한다".</b> 열기와 연결이 호출부에 흩어지면 참여는 있는데 묶음이
 * 없는 행(또는 그 반대인 고아 묶음)이 조용히 생기고, 그 행은 P4 의 {@code bundle_id NOT NULL} 승격에서 뒤늦게 걸린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationBundleDomainService {

  private final ParticipationBundleRepository participationBundleRepository;
  private final ParticipationRepository participationRepository;

  /**
   * 참여를 묶음에 붙인다. {@code reusableBundleId} 가 있으면 그 묶음에, 없으면 새로 열어서 붙인다.
   *
   * <p>순서가 <b>참여 INSERT → 묶음 INSERT → 연결 UPDATE</b> 인 이유는 참여의 조건부 INSERT 가 원시 SQL 이라
   * 컬럼을 더하기 위험하기 때문이다({@code ParticipationRepository#linkBundle} javadoc). 같은 트랜잭션이라 중간에
   * 실패하면 셋 다 롤백된다.
   *
   * <p>호출 측 {@code @Transactional} 필수 — 세 쓰기가 한 단위여야 한다.
   *
   * <p>⚠️ {@code participation} 은 <b>영속성 컨텍스트가 관리하지 않는 인스턴스</b>여야 한다. 관리되는 인스턴스를 넘기면
   * {@code linkBundle} 이 메모리 값을 바꾼 뒤 dirty checking 이 UPDATE 를 한 번 더 내보내 CAS 를 우회한다.
   *
   * @param reusableBundleId 재사용할 묶음 id. {@code null} 이면 새로 연다
   * @param refundAccount 새 묶음에 심을 계좌. <b>{@code reusableBundleId} 가 있으면 쓰이지 않으며 {@code null}
   *     이어도 된다</b> — 재사용이면 그 묶음이 이미 가진 계좌가 정본이다. 둘 다 없으면 {@code
   *     ParticipationBundle#open} 이 {@code PARTICIPATION_REQUIRED_FIELD_MISSING} 으로 잡는다(NPE 아님)
   */
  public void attach(
      final Participation participation,
      final Long reusableBundleId,
      final Long shippingAddressId,
      final long shippingFee,
      final RefundAccount refundAccount,
      final Instant dueAt,
      final Instant now) {
    Long bundleId =
        reusableBundleId != null
            ? reusableBundleId
            : participationBundleRepository
                .save(
                    ParticipationBundle.open(
                        participation.getBuncheolId(),
                        participation.getParticipantId(),
                        shippingAddressId,
                        shippingFee,
                        refundAccount,
                        dueAt))
                .getId();

    // 🔴 영향 행이 1이 아니면 멈춘다. 조용히 넘어가면 "참여는 있는데 묶음이 없는" 행이 남고, 그 행은
    // P4 의 bundle_id NOT NULL 승격에서야 발견된다 — 그때는 원인 트랜잭션이 이미 사라진 뒤다.
    //
    // 실패 사유는 둘인데 성격이 다르다:
    //   ① 재사용하려던 묶음이 그 사이 닫혔다 — <b>정당한 경합</b>. 재시도하면 새 묶음으로 정상 진입하므로
    //      409(CONFLICT)가 맞다.
    //   ② 방금 INSERT 한 행의 bundle_id 가 이미 차 있다 — <b>있을 수 없는 일</b>(내부 불변식 위반).
    // 한 CAS 로 묶여 있어 둘을 응답에서 가르지 않는다(가르려면 스냅샷 재조회가 필요한데, 그 조회는
    // REPEATABLE READ 라 방금 CAS 가 본 current read 와 다른 답을 낼 수 있어 오히려 틀린 분류를 만든다).
    // 대신 로그를 남겨 ②가 실제로 생기면 모니터링에서 정상 경합과 구분되게 한다.
    if (!participationRepository.linkBundle(participation.getId(), bundleId, now)) {
      log.warn(
          "묶음 연결 실패 — 재사용 묶음이 닫혔거나(경합) 이미 연결된 참여다(불변식 위반)."
              + " participationId={}, bundleId={}, reused={}",
          participation.getId(),
          bundleId,
          reusableBundleId != null);
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
    participation.linkBundle(bundleId);
  }

  /**
   * 슬롯 하나가 종료됐을 때 그 묶음도 끝났는지 판정해 닫는다. 살아 있는 슬롯이 남아 있으면 아무것도 하지 않는다.
   *
   * <p>{@code bundleId} 가 {@code null} 이면(배포선 창에서 생긴 미연결 행) 조용히 넘어간다 — 그 행은 배포 직후 백필이 채운다.
   *
   * <p>호출 측 {@code @Transactional} 필수.
   */
  public void closeIfEmpty(final Long bundleId, final Instant now) {
    if (bundleId == null) {
      return;
    }
    participationBundleRepository.closeIfNoActiveSlots(bundleId, now);
  }

  /** 묶음 단건. 묶음 단위 API 의 진입점이다. */
  public Optional<ParticipationBundle> findById(final Long bundleId) {
    return participationBundleRepository.findById(bundleId);
  }

  /** 참여 한 건의 묶음. 미연결 행(배포선 창)이면 비어 있다. */
  public Optional<ParticipationBundle> findByParticipation(final Participation participation) {
    return participation.getBundleId() == null
        ? Optional.empty()
        : participationBundleRepository.findById(participation.getBundleId());
  }

  /**
   * 참여 목록의 묶음을 한 번에 읽어 {@code 묶음 id → 묶음} 으로 돌려준다 (목록 화면 N+1 방지).
   *
   * <p>미연결 참여는 결과에 없다 — 호출부는 {@code map.get(bundleId)} 가 {@code null} 일 수 있음을 전제할 것.
   */
  public Map<Long, ParticipationBundle> findAllByParticipations(
      final Collection<Participation> participations) {
    return participationBundleRepository
        .findAllByIds(participations.stream().map(Participation::getBundleId).toList())
        .stream()
        .collect(Collectors.toMap(ParticipationBundle::getId, Function.identity()));
  }

  /**
   * 배치 조회 결과에서 이 참여의 계좌를 꺼낸다. 미연결 참여(배포선 창)는 {@code null}.
   *
   * <p>{@link #findAllByParticipations} 가 정의한 규약("{@code map.get(bundleId)} 가 null 일 수 있다")을 지키는
   * 자리라 같은 클래스에 둔다 — 호출부마다 3항 연산을 흩뿌리면 한 곳만 빠뜨렸을 때 NPE 다.
   */
  public static RefundAccount refundAccountOf(
      final Map<Long, ParticipationBundle> bundleById, final Participation participation) {
    ParticipationBundle bundle = bundleById.get(participation.getBundleId());
    return bundle == null ? null : bundle.getRefundAccount();
  }

  /**
   * <b>불완전할 수 있는 목록</b>을 위한 배송비 귀속 ({@link ShippingFeeAttribution}). 목록에 걸린 묶음들의 형제 슬롯을
   * 대신 읽어 오므로 <b>페이지네이션·필터가 걸린 목록에도 안전</b>하다 — 그런 목록을 {@link
   * ShippingFeeAttribution#ofAllSlots} 에 직접 넘기면 페이지 조각 안에서 carrier 를 다시 뽑아 배송비가 두 번 걷힌다.
   *
   * <p>이미 묶음별 슬롯을 빠짐없이 들고 있는 호출부(개최 관리·내 참여)는 이 조회가 낭비이므로 {@code ofAllSlots} 를
   * 직접 쓴다.
   */
  public ShippingFeeAttribution shippingFeeAttributionFor(
      final Collection<Participation> participations) {
    return shippingFeeAttributionFor(participations, null);
  }

  /**
   * 묶음을 <b>이미 배치로 읽어 둔</b> 호출부용. 형제 슬롯만 추가로 읽는다 — 넘기지 않으면 여기서 다시 조회한다.
   */
  public ShippingFeeAttribution shippingFeeAttributionFor(
      final Collection<Participation> participations,
      final Map<Long, ParticipationBundle> knownBundleById) {
    List<Long> bundleIds =
        participations.stream()
            .map(Participation::getBundleId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (bundleIds.isEmpty()) {
      return ShippingFeeAttribution.empty();
    }
    List<Participation> allSlots = participationRepository.findAllByBundleIds(bundleIds);
    Map<Long, ParticipationBundle> bundleById =
        knownBundleById != null
            ? knownBundleById
            : participationBundleRepository.findAllByIds(bundleIds).stream()
                .collect(Collectors.toMap(ParticipationBundle::getId, Function.identity()));
    return ShippingFeeAttribution.ofAllSlots(allSlots, bundleById);
  }

  /**
   * 묶음을 <b>이미 읽어 둔</b> 호출부용 배송비 귀속. 형제 슬롯만 추가로 읽으므로 묶음을 다시 조회하지 않는다.
   *
   * <p>{@code bundle} 이 {@code null}(미연결 참여)이면 조회 없이 빈 판정을 준다.
   */
  public ShippingFeeAttribution shippingFeeAttributionOf(final ParticipationBundle bundle) {
    return bundle == null
        ? ShippingFeeAttribution.empty()
        : ShippingFeeAttribution.ofBundle(
            bundle, participationRepository.findAllByBundleIds(List.of(bundle.getId())));
  }

  /** 참여 <b>한 건</b>을 위한 배송비 귀속. 위와 같은 보장을 준다. */
  public ShippingFeeAttribution shippingFeeAttributionFor(final Participation participation) {
    return shippingFeeAttributionFor(List.of(participation));
  }

  /** 분철 취소 cascade·자동 마감 뒤에 비게 된 묶음을 일괄로 닫는다. 호출 측 {@code @Transactional} 필수. */
  public int closeEmptyByBuncheolId(final Long buncheolId, final Instant now) {
    return participationBundleRepository.closeEmptyByBuncheolId(buncheolId, now);
  }

  /**
   * 묶음 기한을 뒤로 민다 (개최자 반려). 이미 더 뒤인 기한은 건드리지 않는다 — 반려가 기한을 <b>앞으로</b> 당기면
   * 「제외」가 열려 버린다.
   *
   * <p>{@code bundleId} 가 {@code null}(미연결 참여)이면 조용히 넘어간다.
   */
  public void extendDueAt(final Long bundleId, final Instant dueAt, final Instant now) {
    if (bundleId == null) {
      return;
    }
    participationBundleRepository.extendDueAt(bundleId, dueAt, now);
  }

  /** 성사 확정 시 기한 없이 열려 있던 묶음에 입금 기한을 채운다. 호출 측 {@code @Transactional} 필수. */
  public int assignDueAtByBuncheolId(
      final Long buncheolId, final Instant dueAt, final Instant now) {
    return participationBundleRepository.assignDueAtByBuncheolId(buncheolId, dueAt, now);
  }
}
