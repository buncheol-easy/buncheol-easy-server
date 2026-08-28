package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 사람이 한 분철에서 한 번에 신청한 슬롯들의 묶음. <b>현실의 돈 단위</b>다 — 이체 1회 · 배송비 1회 · 택배 1개가 전부 이 단위와 1:1로
 * 맞아떨어진다.
 *
 * <p>도입 이유는 표시 개선이 아니라 <b>돈이 새는 것</b>이다. 배송비·배송지·환불계좌·입금기한이 슬롯 행에 흩어져 있어서 그 행이 취소되면 값도 같이
 * 죽었다 (docs/62 M-01). prod 실사에서 실제로 확인됐다 — 다세대 그룹 3건이 배송비 최대 3,000 / 합계 6,000 이라 사람 기준으로 묶으면
 * 9,000원이 사라진다 (docs/70 §12). <b>값이 행에 살면 그 행이 죽을 때 값도 죽는다</b> 는 것이 이 테이블의 존재 이유다.
 *
 * <p><b>경계</b> — 묶음은 {@code (분철, 사람, 결제 사이클)} 단위다. 추가 모집분은 <b>새 묶음</b>이라 기존 묶음에 섞이지 않는다 (docs/70
 * 결정 2). 그래서 한 묶음 안의 슬롯은 상태가 갈리지 않고, 「보냈어요」·입금확인·「제외」가 전부 묶음 단위로 성립한다 (docs/71 §1).
 *
 * <p>⚠️ <b>"사이클" 이 추가 모집끼리를 묶는다는 뜻은 아니다 — 추가 모집은 슬롯마다 새 묶음이다.</b> 같은 입금 수집중 창에서 같은 사람이
 * 두 번 신청하면 묶음이 2개 생기고, 배송비 재부과가 붙으면 배송비도 2회다. 각 추가 모집 슬롯이 <b>개별 24h 기한</b>으로 따로 진입하고
 * 이체도 따로 하기 때문이다. 데이터만으로는 "한 번에 신청했는지" 를 알 수 없어 <b>쪼개는 쪽이 보수적</b>이다(합쳐 놓으면 서로 다른 두
 * 이체가 한 묶음에 섞여 되돌릴 수 없다 — 백필 STEP 3 도 같은 이유로 행별 1:1이다). 실측 prod 0건 · staging 1건이고, 화면과 청구가
 * 일치하므로 수용한다 (docs/80 §3-6 알려진 한계). 자동 병합은 규칙이 훨씬 복잡해져 기각됐다.
 *
 * <p>📌 {@code findActiveByBuncheolIdAndParticipantId}·{@code findAllByBuncheolId}·{@link #isActive()} 는
 * 아직 프로덕션 미사용이다(P3 화면이 쓸 자리). {@code findById} 는 P2-c 의 {@code findByParticipation} 이 쓴다.
 *
 * <p><b>활성 묶음 유니크는 없다</b> (docs/71 §8-3). 추가 모집·재신청이 새 묶음이어야 해서 한 사람이 한 분철에 활성 묶음 2개를 가질 수 있어야
 * 하기 때문이다. 중복 방지는 앱 가드가 하고, LEGACY 1인 1슬롯 보호는 {@code
 * participations.uq_participations_legacy_active_participant} 가 그대로 계속한다.
 *
 * <p><b>P2-c 완료</b> — 이 엔티티가 환불 계좌·입금자명의 <b>정본</b>이다. 응답·알림·어드민이 전부 여기서 읽고,
 * {@code participations.refund_*} 는 더 이상 쓰이지도 읽히지도 않는다(P4 에서 삭제).
 */
@Entity
@Table(name = "participation_bundles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipationBundle extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  @Column(name = "participant_id", nullable = false, updatable = false)
  private Long participantId;

  // 묶음당 배송지 1개 = 택배 1개. 종료된 묶음이 참조하던 배송지를 사용자가 삭제하면 FK ON DELETE SET NULL 로 NULL 이 된다.
  @Column(name = "shipping_address_id", updatable = false)
  private Long shippingAddressId;

  // 배송비는 묶음이 소유한다. 슬롯을 몇 개 담든 1회, 슬롯 하나가 취소돼도 불변 — M-01 이 구조적으로 닫히는 지점이다.
  // 추가 모집으로 새 묶음이 생기면 새 택배이므로 그 묶음에 다시 1회 부과된다.
  @Column(name = "shipping_fee", nullable = false, updatable = false)
  private long shippingFee;

  // 개최자 통장 대조 키(예금주 = 입금자명) + 환불이 필요할 때 돌려줄 계좌. 참여 시점 스냅샷이다.
  // participations 와 컬럼명(refund_*)이 같아 VO 를 그대로 재사용한다.
  @Embedded private RefundAccount refundAccount;

  // 입금 기한. C2C 는 이 시각이 지나도 자동 취소하지 않는다 — 대신 이 시각부터 개최자 「제외」가 열린다 (docs/71 §8-1).
  // 즉 표시 전용이 아니라 "개최자가 정리에 나설 수 있게 되는 시각" 이라는 기능적 의미를 갖는다.
  @Column(name = "due_at")
  private Instant dueAt;

  // 참여자 「보냈어요」 마킹 시각. 묶음 1회이며 분쟁 시 참여자 측 타임스탬프 증거다.
  @Column(name = "payment_sent_at")
  private Instant paymentSentAt;

  // 활성 슬롯이 0이 된 시각. NULL 이면 활성 묶음이다.
  @Column(name = "closed_at")
  private Instant closedAt;

  /**
   * 새 묶음을 연다. 배송지·배송비·환불계좌는 <b>참여 시점 스냅샷</b>이라 생성 후 바뀌지 않는다({@code
   * updatable = false}).
   *
   * <p>{@code dueAt} 은 <b>즉시 입금 경로(LEGACY·C2C 추가 모집)에서만</b> 값이 있다. C2C 신청(APPLIED)은 성사 확정
   * 시점에 기한이 정해지므로 {@code null} 로 열고, 나중에 {@code
 * ParticipationBundleRepository#assignDueAtByBuncheolId} 가 분철 범위로 채운다 — 신청 구간에는 입금 기한이라는
   * 개념이 없다.
   *
   * <p>⚠️ 검증을 여기서 하는 이유: 이 필드들은 DB 가 {@code NOT NULL} 이라, 없는 채로 저장하면
   * {@code BusinessException} 이 아니라 {@code Column 'refund_bank' cannot be null} <b>500</b> 이 난다.
   */
  public static ParticipationBundle open(
      final Long buncheolId,
      final Long participantId,
      final Long shippingAddressId,
      final long shippingFee,
      final RefundAccount refundAccount,
      final Instant dueAt) {
    if (buncheolId == null || participantId == null || refundAccount == null) {
      throw new BusinessException(ErrorCode.PARTICIPATION_REQUIRED_FIELD_MISSING);
    }
    ParticipationBundle bundle = new ParticipationBundle();
    bundle.buncheolId = buncheolId;
    bundle.participantId = participantId;
    bundle.shippingAddressId = shippingAddressId;
    bundle.shippingFee = shippingFee;
    bundle.refundAccount = refundAccount;
    bundle.dueAt = dueAt;
    return bundle;
  }

  public boolean isActive() {
    return closedAt == null;
  }

  // ⚠️ 이 엔티티에는 상태 변경 setter 를 두지 않는다 — closed_at·payment_sent_at·due_at 은 전부 경쟁이
  // 있거나 범위 일괄이라 CAS·벌크 UPDATE 로만 쓴다(ParticipationBundleRepository). setter 를 두면
  // "읽고 판단하고 쓰는" 코드가 생기고, 그 순간 같은 묶음의 두 슬롯이 동시에 취소될 때 양쪽 다
  // "아직 하나 남았다" 를 보고 둘 다 안 닫는다. 게다가 이 엔티티는 clearAutomatically 벌크 UPDATE 로만
  // 갱신되므로 dirty checking 이 섞이면 그 갱신과 충돌한다.
}
