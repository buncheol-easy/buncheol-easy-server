package buncheoleasy.buncheol.domain;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.page.Cursorable;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buncheols")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Buncheol extends TimestampedEntity implements Cursorable {

  private static final int TITLE_MAX_LENGTH = 200;
  private static final int DESCRIPTION_MAX_LENGTH = 700;
  private static final int PURCHASE_SITE_MAX_LENGTH = 200;
  private static final int OPEN_CHAT_URL_MAX_LENGTH = 200;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "host_id", nullable = false, updatable = false)
  private Long hostId;

  // 대상 K-pop 그룹 FK.
  @Column(name = "group_id", nullable = false)
  private Long groupId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "purchase_site", nullable = false, length = 200)
  private String purchaseSite;

  // 참여 신청 마감 시각. 이 시각 이후엔 새 참여가 불가하고, 마감 판정(진행확정/취소)이 이뤄진다.
  @Column(nullable = false)
  private Instant deadline;

  // 분철 진행 최소 인원. 마감 시점에 입금확인된(CONFIRMED) 참여자가 이 수 이상이면 진행확정, 미만이면 취소된다.
  @Column(name = "min_headcount", nullable = false)
  private int minHeadcount;

  @Embedded private ShippingFeePolicy shippingFeePolicy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private BuncheolStatus status;

  // 분철이 RECRUITING → CONFIRMED/CANCELLED 로 마감 판정된 시각 (호스트 취소 또는 마감 스케줄러).
  // C2C 는 개최자 성사 확정(PAYMENT_COLLECTING 진입) 시각.
  @Column(name = "finalized_at")
  private Instant finalizedAt;

  // --- C2C 플로우 병존 (docs/46 §0.1) ---

  // 분철 단위 플로우 구분. 기존 분철·운영진 개최는 LEGACY 로 현행 그대로, 새 플로우는 C2C 분철에만 적용한다.
  @Enumerated(EnumType.STRING)
  @Column(name = "flow_type", nullable = false, length = 10, updatable = false)
  private FlowType flowType;

  // C2C: 개최자 성사 확정 시 산정한 일괄 입금 기한. PAYMENT_COLLECTING 진입 CAS 에서 세팅한다.
  @Column(name = "payment_due_at")
  private Instant paymentDueAt;

  // C2C: 개최자 오픈채팅 링크(선택) — 참여자 소통 채널 (docs/46 §7.1-10).
  @Column(name = "open_chat_url", length = 200)
  private String openChatUrl;

  // C2C: 확정 시점 개최자 계좌 스냅샷. 확정 후 개최자가 프로필 계좌를 바꿔도 이미 안내된 입금 계좌가
  // 어긋나지 않게 고정한다 (docs/46 §4.7-B1). LEGACY 는 사용하지 않는다(실시간 조회 유지).
  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "bank", column = @Column(name = "payment_bank", length = 50)),
    @AttributeOverride(name = "account", column = @Column(name = "payment_account", length = 50)),
    @AttributeOverride(name = "holder", column = @Column(name = "payment_holder", length = 50))
  })
  private BankAccount paymentAccount;

  public static Buncheol create(final Long hostId, final BuncheolParams params, final Instant now) {
    return new Buncheol(hostId, params, now);
  }

  private Buncheol(final Long hostId, final BuncheolParams params, final Instant now) {
    validate(hostId, params, now);
    this.hostId = hostId;
    this.groupId = params.groupId();
    this.title = params.title();
    this.description = params.description();
    this.purchaseSite = params.purchaseSite();
    this.deadline = params.deadline();
    this.minHeadcount = params.minHeadcount();
    this.shippingFeePolicy = ShippingFeePolicy.of(params.gs25ShippingFee(), params.cuShippingFee());
    this.status = BuncheolStatus.RECRUITING;
    this.flowType = params.flowType();
    this.openChatUrl = params.openChatUrl();
  }

  public boolean isC2c() {
    return flowType == FlowType.C2C;
  }

  public void updateContent(final String title, final String description) {
    validateTitle(title);
    validateDescription(description);
    this.title = title;
    this.description = description;
  }

  // 수정 계약 (docs/51 §3-1-2): null = 유지(필드를 안 보내는 구 클라이언트 호환), 빈 문자열·공백 = 제거, 값 = 검증 후 교체.
  public void updateOpenChatUrl(final String value) {
    if (value == null) {
      return;
    }
    if (value.isBlank()) {
      this.openChatUrl = null;
      return;
    }
    validateOpenChatUrl(value);
    this.openChatUrl = value;
  }

  public void validateOwner(final Long userId) {
    if (!isHost(userId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION);
    }
  }

  public void validateRecruiting(final Instant now) {
    if (status != BuncheolStatus.RECRUITING) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
    if (!deadline.isAfter(now)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
  }

  public void validateShippingMethodSupported(final ShippingMethod shippingMethod) {
    boolean supported =
        switch (shippingMethod) {
          case GS25_HALF -> shippingFeePolicy.gs25ShippingFee() != null;
          case CU_HALF -> shippingFeePolicy.cuShippingFee() != null;
        };
    if (!supported) {
      throw new BusinessException(ErrorCode.PARTICIPATION_SHIPPING_METHOD_NOT_SUPPORTED);
    }
  }

  // 참여자가 선택한 배송수단의 배송비. 참여자가 입금할 총액(멤버 금액 + 배송비) 계산에 사용한다.
  public long shippingFeeFor(final ShippingMethod shippingMethod) {
    return shippingFeePolicy.feeFor(shippingMethod);
  }

  public boolean isHost(final Long userId) {
    return hostId.equals(userId);
  }

  private void validate(final Long hostId, final BuncheolParams params, final Instant now) {
    validateHostAndParams(hostId, params);
    validateGroupId(params.groupId());
    validateTitle(params.title());
    validateDescription(params.description());
    validatePurchaseSite(params.purchaseSite());
    validateDeadline(params.deadline(), now);
    validateMinHeadcount(params.minHeadcount());
    validateFlowType(params.flowType());
    validateOpenChatUrl(params.openChatUrl());
  }

  private void validateFlowType(final FlowType value) {
    if (value == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  // 오픈채팅 링크는 선택 입력. 있으면 카카오 오픈채팅 도메인 형식만 허용하고(임의 외부 링크 유도 방지 — docs/46 §4.6),
  // 공백·제어문자가 저장돼 화면·알림에 그대로 노출되는 것을 막는다.
  private void validateOpenChatUrl(final String value) {
    if (value == null) {
      return;
    }
    if (value.length() > OPEN_CHAT_URL_MAX_LENGTH
        || !value.startsWith("https://open.kakao.com/")
        || value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
      throw new BusinessException(ErrorCode.BUNCHEOL_OPEN_CHAT_URL_INVALID);
    }
  }

  private void validateHostAndParams(final Long hostId, final BuncheolParams params) {
    if (hostId == null || params == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateGroupId(final Long value) {
    if (value == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateTitle(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    if (value.length() > TITLE_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  private void validateDescription(final String value) {
    if (value != null && value.length() > DESCRIPTION_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  private void validatePurchaseSite(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    if (value.length() > PURCHASE_SITE_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  private void validateDeadline(final Instant deadline, final Instant now) {
    if (deadline == null || !deadline.isAfter(now)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_DEADLINE_INVALID);
    }
    // 마감은 정각(매시 0분 0초)만 허용 — 매시 정각 cron 으로 정밀 마감하기 위함. KST(+9, 정시 오프셋)라 UTC 시각경계와
    // KST 시각경계가 일치하므로, epochSecond 가 3600 으로 나눠떨어지고 나노초가 0인지로 검증한다.
    if (deadline.getEpochSecond() % 3600 != 0 || deadline.getNano() != 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_DEADLINE_NOT_ON_THE_HOUR);
    }
  }

  private void validateMinHeadcount(final int minHeadcount) {
    if (minHeadcount < 1) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_INVALID);
    }
  }
}
