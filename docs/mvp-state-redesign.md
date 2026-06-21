# MVP 재설계: 경매/PG 제거 + 고정가 선착순 + 수동 입금확인

> 상태: **설계 확정 대기** (코드 변경 전 합의용 문서)
> 대상 라인: MVP 레포 (경매·PG 미사용). 경매/PG 버전은 별도 레포/태그로 보존.

---

## 0. 목적 / 범위

빠른 MVP 출시를 위해 **경매/입찰 + PG(Toss) 결제**를 제거하고, 아래 모델로 전환한다.

- 개최자가 분철 생성 시 **멤버별 고정 금액**과 **분철 진행 최소 인원**을 설정한다.
- 참여자는 멤버 슬롯을 **선착순**으로 점유한다. 점유 즉시 개최자 계좌가 노출되고 **30분 입금 타이머**가 시작된다.
- 입금은 **은행앱(외부)** 에서 처리한다. 시스템은 입금 사실을 감지하지 못한다.
- 개최자가 30분 내 **수동으로 입금확인**하면 참여 확정.
- 마감 시점에 **확정 참여자 ≥ 최소 인원**이면 진행, 미달이면 분철 취소.

### 운영 전제 (이번 MVP)
- **개최는 운영자만** 가능하다. 일반 유저는 참여만 한다.
- 따라서 **환불은 운영자가 오프라인으로 직접 처리**한다. 환불 상태/알림은 앱에서 추적하지 않는다(추후 PG·경매 확장 시 보강).

---

## 1. 확정된 제품 결정

| # | 항목 | 결정 |
|---|---|---|
| 1 | 멤버 정원 모델 | **멤버당 1명 선착순.** 한 슬롯을 점유하면 그 멤버는 마감 |
| 2 | 최소 인원 단위 | **분철 전체 합산.** `Buncheol.minHeadcount` 하나로 deadline에 CONFIRMED 총합 판정 |
| 3 | Payment 도메인 | **MVP 레포에서 완전 제거** (엔티티·컨트롤러·콜백·스케줄러·이벤트) |
| 4 | 마감 vs 30분 타이머 충돌 | **deadline 시점 CONFIRMED만 카운트.** 마감 판정은 잔여 입금대기를 건드리지 않고, 입금 만료 스케줄러가 폴링 주기 내 `PAYMENT_TIMEOUT` 취소+알림을 단독 처리(중복 알림 방지) |
| 5 | 환불 추적 | **추적 안 함.** 환불계좌만 저장, 실제 환불은 운영자 오프라인 |
| 6 | 입금 자가신고 | **없음.** 호스트 수동확인만 |

---

## 2. 새 상태머신 — Buncheol

경매/정산 잔재(`CLOSED`, `PAID`, `SETTLING`)를 제거하고 3개로 축약한다.

| 상태 | 워딩(안) | 존재 이유 | 진입 트리거 |
|---|---|---|---|
| `RECRUITING` | 모집중 | 참여 접수 중. 유저에게 "참여 가능" 노출 | 분철 생성 |
| `CONFIRMED` | 진행확정 | deadline 도달 & 확정참여 ≥ 최소인원. 운영자가 굿즈 구매·배송 진행 | 마감 스케줄러 |
| `CANCELLED` | 취소 | 모집 실패 또는 개최자 취소. 확정 참여자 환불 대상(오프라인) | 호스트 취소 / 마감 미달 |

```
                 host.cancel (RECRUITING 한정)
        ┌───────────────────────────────────────────► CANCELLED
        │                                               ▲
RECRUITING ── deadline 도달 ──┬─ 확정참여 ≥ min ─► CONFIRMED  │
                              └─ 확정참여 <  min ──────────────┘
```

### 설계 노트
- 기존 `CLOSED`(마감 후 결과 판정 전) 중간 상태는 **불필요** — 마감 즉시 카운트해서 `CONFIRMED`/`CANCELLED` 둘 중 하나로 직결.
- **`FINISHED`(완료)** 는 이번 MVP 코어에서 제외. **추후 운송장 트래킹 API**를 연동해 배송 완료를 자동 종결하는 시점에 추가한다(§13).
- `closedAt` → `finalizedAt` 으로 의미 정리(진행확정/취소가 확정된 시각). 컬럼명은 유지하거나 리네임.

---

## 3. 새 상태머신 — Participation

입찰/순위/승계(`ACTIVE_BID`, `AWAITING_PAYMENT`, `PAYMENT_REPORTED`, `FAILED`, `closedRank`, 차순위 이양)를 전부 제거하고 **3개**로 축약한다.

| 상태 | 워딩(안) | 존재 이유 | 진입 트리거 |
|---|---|---|---|
| `AWAITING_PAYMENT` | 입금확인중 | 슬롯 선점 완료, 개최자 계좌 노출, 입금 타이머 가동(`min(+30분, deadline)`), 환불계좌 입력 완료. **호스트 확인 대기**. 최소인원 카운트 **제외** | 참여(슬롯 점유) |
| `CONFIRMED` | 참여확정 | 개최자가 입금 수동확인. **최소인원 카운트 대상**, 분철 진행 시 배송 대상 | 호스트 입금확인 |
| `CANCELLED` | 참여취소 | 종료. 슬롯 반환 여부·유저 안내 메시지 결정용으로 `cancelReason` 보유 | 타임아웃 / 자발취소 / 분철취소 / 마감컷오프 |

```
참여 → AWAITING_PAYMENT ──── host.confirmPayment ───► CONFIRMED
            │                                            │
            │ 30분 타임아웃(스케줄러)  ┐                  │ 분철 CANCELLED 시
            │ 참여자 자발 취소         ├─► CANCELLED ◄────┘ (BUNCHEOL_CANCELLED)
            │ 마감 컷오프             ┘    (cancelReason)
```

### `cancelReason` (enum)
| 값 | 의미 | 슬롯 반환 |
|---|---|---|
| `PAYMENT_TIMEOUT` | 입금 기한(dueAt=min(참여+30분, deadline)) 내 입금확인 안 됨 → 자동 취소. 모집 중 30분 만료든 마감 시점 컷오프든 동일 사유(어느 스케줄러가 먼저 잡는지만 다름) | O (모집 중이면 재선착) |
| `SELF_CANCELLED` | 참여자가 입금확인중 단계에서 직접 취소 | O |
| `BUNCHEOL_CANCELLED` | 분철 취소로 일괄 취소(입금확인중·확정 모두). 확정건은 오프라인 환불 대상 | — |

> 초안에 있던 `DEADLINE_CUTOFF` 는 제거. dueAt 가 deadline 으로 클램프되므로 마감 시점에 남은 입금확인중 참여는 **이미 입금 기한이 지난 상태** = `PAYMENT_TIMEOUT` 과 같은 트리거다. 별도 사유로 두면 마감 직전 참여자가 "만료 스케줄러 vs 마감 판정" 의 race 에 따라 라벨이 갈려 무의미하므로 합쳤다. **취소·알림 책임은 입금 만료 스케줄러로 일원화**한다 — 마감 판정(`finalizeAsConfirmed`)은 잔여 입금확인중 참여를 건드리지 않고, 만료 스케줄러가 `PAYMENT_TIMEOUT` 으로 취소하며 참여자에게 자동취소 알림(`PAYMENT_EXPIRED`)을 보낸다. 두 경로가 같은 참여에 알림을 중복 발송하던 race 를 원천 차단(마감 임박 참여의 컷오프 위험은 참여 시 프론트 안내로 사전 고지). 트레이드오프: 마감~다음 폴링(≤주기) 동안 CONFIRMED 분철에 입금확인중 자식이 잠시 남고, 만료 스케줄러가 꺼지면 정리되지 않으므로 운영상 항상 켜둬야 한다.

### 설계 노트
- 타임아웃/자발취소/마감컷오프/분철취소를 **별도 종료상태로 쪼개지 않고** `CANCELLED` 단일 상태 + `cancelReason` 으로 표현 → "무의미한 상태 축약" 원칙.
- 기존 `FAILED`(낙찰 실패)는 경매 개념이라 **소멸**.
- `PAYMENT_REPORTED`(자가신고)는 결정 6에 따라 **소멸**.
- **CONFIRMED 이후 참여자 자발 취소 불가** (이미 입금됨). 취소가 필요하면 개최자에게 문의 → 운영자 처리. `cancel`은 `AWAITING_PAYMENT`에서만 허용.

---

## 4. 선착순 슬롯 점유 모델 (BuncheolMember)

"멤버당 1명 선착순"이므로 한 멤버 슬롯에 **활성 참여가 최대 1개**여야 한다. 동시 클릭 race를 막기 위해 멤버 슬롯에 점유 상태를 두고 **CAS로 점유/반환**한다(프로젝트 CAS 컨벤션과 일치).

### BuncheolMember 변경
- `bidMinPrice`(최저 입찰가) → **`price`(고정 판매가)**. 100원 단위 검증.
- 점유 상태 추가:
  - `status`: `AVAILABLE` | `TAKEN`
  - `occupiedParticipationId` (nullable): 현재 점유 중인 participation id (반환 CAS의 소유권 가드용)

### 점유 / 반환 흐름 (CAS)
```
[참여 시도]
  UPDATE buncheol_members
     SET status = 'TAKEN', occupied_participation_id = :pid
   WHERE id = :memberId AND status = 'AVAILABLE'      -- 1행 갱신 성공해야 슬롯 확보
  → 0행이면 PARTICIPATION_SLOT_TAKEN(이미 점유됨, *_CONFLICT 계열)

[타임아웃/자발취소 → 슬롯 반환]
  UPDATE buncheol_members
     SET status = 'AVAILABLE', occupied_participation_id = NULL
   WHERE id = :memberId AND occupied_participation_id = :pid
```

> 점유 CAS 성공 후 participation INSERT. 기존 `Participation`의 conditional INSERT(ReflectionUtils로 id 주입) 메커니즘을 이 흐름에 맞게 재사용/조정한다. 점유와 INSERT의 원자성은 같은 `@Transactional` 경계에서 처리하되, 더티체킹과 `@Modifying` CAS 혼용 금지 원칙(메모리)을 지켜 **둘 다 명시적 쓰기**로 구성한다.

---

## 5. 엔티티별 필드 변경표

### Buncheol
| 변경 | 필드 | 비고 |
|---|---|---|
| ADD | `minHeadcount` (int, ≥1) | 분철 진행 최소 인원 |
| KEEP | hostId, groupId, title, description, purchaseSite, deadline, shippingFeePolicy | 그대로 |
| CHANGE | status enum | RECRUITING/CONFIRMED/CANCELLED |
| RENAME | `closedAt` → `finalizedAt` | 진행확정·취소 확정 시각 |
| METHOD | `close()` 제거, `finalizeAsConfirmed()`/`finalizeAsCancelled()` 신설 | 마감 판정용 (또는 CAS 쿼리로 대체) |
| METHOD | `cancel()` 유지 | RECRUITING→CANCELLED |

### BuncheolMember
| 변경 | 필드 | 비고 |
|---|---|---|
| RENAME+규칙 | `bidMinPrice` → `price` | 100원 단위, >0 |
| ADD | `status` (AVAILABLE/TAKEN) | 선착순 점유 |
| ADD | `occupiedParticipationId` | 반환 CAS 소유권 가드 |
| REMOVE | `validateBidAmount()` | 입찰 개념 소멸 |

### Participation
| 변경 | 필드 | 비고 |
|---|---|---|
| REMOVE | `bidAmount` | → 점유 시점 멤버 `price` 스냅샷 `amount`로 대체 |
| ADD | `amount` (long) | 점유 시점 가격 스냅샷(이후 가격변경 무영향) |
| ADD | `refundAccount` (@Embedded BankAccount) | 환불계좌, 참여 시 필수 |
| ADD | `cancelReason` (enum) | §3 |
| CHANGE | `dueAt` | 의미 재정의: **입금 만료시각 = `min(점유시각 + 30분, deadline)`**. deadline을 절대 못 넘음 |
| REMOVE | `closedRank`, `failReason`, `paymentReportedAt` | 경매/자가신고 소멸 |
| RENAME | `paymentConfirmedAt` → `confirmedAt`, `finalizedAt` 유지 | |
| REMOVE | `@Version` | 전이를 전부 status CAS로 가드 → 낙관적 락 불필요(단순화). 유지해도 무방하나 제거 권장 |
| METHOD | create/confirmPayment/expire/cancelBySelf/cancelByDeadline/cancelByBuncheol 로 재작성 | 기존 award/promote/report/reject/completePayment 전부 제거 |

---

## 6. VO 설계

### 환불계좌 — BankAccount 재사용
기존 `user.domain.BankAccount`(bank/account/holder, compact constructor 검증)를 `Participation`에 `@Embedded`로 재사용. 컬럼명 충돌 방지:
```
refund_bank / refund_account / refund_holder
```
참여 요청 본문에 필수 포함(참여와 동시에 입력).

### 가격 100원 단위 — Price VO (record + AttributeConverter)
프로젝트 VO 컨벤션(단일 컬럼 record + 변환기)에 맞춰 `Price` 신설:
```java
public record Price(long value) {
  public Price {
    if (value <= 0 || value % 100 != 0) {
      throw new BusinessException(ErrorCode.INVALID_PRICE_UNIT); // 신규 ErrorCode
    }
  }
}
```
`BuncheolMember.price` 와 `Participation.amount` 가 공유. (둘 다 long 직접 보유 + 정적 검증 헬퍼로 가도 무방 — 팀 선호에 맞춰 택1)

---

## 7. 타이머 & 마감 스케줄러

### (신규) 입금 만료 스케줄러 — `ParticipationPaymentExpiryScheduler`
- 주기: 1분 fixedDelay (30분 타이머이므로 분 단위 정밀도면 충분)
- 동작: `AWAITING_PAYMENT` 이고 `dueAt <= now` 인 건을 배치 조회 →
  1. participation CAS: `status AWAITING_PAYMENT → CANCELLED(PAYMENT_TIMEOUT)` (`WHERE status='AWAITING_PAYMENT' AND due_at <= now`)
  2. 멤버 슬롯 반환 (active_member_id 생성컬럼이 NULL 로 풀림, §4)
  3. CAS 성공 건만 `PaymentExpiredEvent` 발행 → 참여자 자동취소 알림(`PAYMENT_EXPIRED`, AFTER_COMMIT). 실제 전이한 쪽만 발행하므로 마감 판정과 경합해도 알림 1회.
- 멱등: status CAS로 중복 실행 방어. 건별 독립 트랜잭션. 마감 직후 잔여 입금확인중 참여의 취소·알림도 이 스케줄러가 단독 담당.

### (개편) 마감 스케줄러 — 기존 `BuncheolAutoCloseScheduler` 재사용
- `selectWinners()` / `ParticipationWonEvent` **제거**.
- deadline 경과 분철별 트랜잭션:
  1. `CONFIRMED` 참여 수 카운트
  2. `count >= minHeadcount` → Buncheol CAS `RECRUITING → CONFIRMED`
     아니면 → Buncheol CAS `RECRUITING → CANCELLED`
  3. 잔여 `AWAITING_PAYMENT` 처리:
     - 진행확정 시: **마감 판정은 건드리지 않음.** 입금 만료 스케줄러가 폴링 주기 내 `PAYMENT_TIMEOUT` 취소 + 자동취소 알림(`PAYMENT_EXPIRED`) 단독 처리 (중복 알림 방지)
     - 분철취소 시: 활성 참여(입금확인중·확정) 일괄 `cancelReason = BUNCHEOL_CANCELLED`
  4. (분철취소 시) `CONFIRMED` 건은 오프라인 환불 대상 — 상태는 `CANCELLED(BUNCHEOL_CANCELLED)` 로 전이. 환불 추적 없음.
- 카운트→판정→CAS는 read-then-CAS. Buncheol CAS가 RECRUITING 가드라 이중 실행 방어.

### (제거) `PaymentDueReminderScheduler` + `DueReminderGuard`
- PG 시대 입금기한 임박 알림. 30분 타이머엔 불필요 → 제거. (5분전 알림톡 등은 추후 옵션.)

---

## 8. API 변경 (초안)

| 메서드 | 엔드포인트 | 변경 | 설명 |
|---|---|---|---|
| POST | `/v1/buncheols` | 변경 | 생성 시 `minHeadcount`, 멤버별 `price`(100원) |
| POST | `/v1/buncheols/{id}/members/{memberId}/participate` | 신규 | **선착순 점유 + 참여**. body: `refundAccount`, `shippingAddressId`, `shippingMethod`. 응답: 개최자 계좌 + `dueAt`(만료시각) |
| POST | `/v1/participations/{id}/confirm` | 변경 | **호스트 입금확인** → CONFIRMED (자가신고 단계 제거) |
| DELETE | `/v1/participations/{id}` | 유지 | 참여자 자발 취소(AWAITING_PAYMENT 한정) |
| GET | `/v1/participations/me` | 유지 | 내 참여 목록 |
| GET | `/v1/buncheols/{id}/participations` | 신규/변경 | 호스트 관리 화면: 입금확인중/확정 목록 + 환불계좌 |
| POST | `/v1/buncheols/{id}/cancel` | 유지 | 호스트 분철 취소 |
| — | `/payment/**`, `/v1/payments/**` | **제거** | checkout/mock/report/expire(승계)/success/fail/cancel 전부 |

> 참여 = 계좌 확인 시점이라는 요구사항을 단일 호출로 충족: `participate` 가 슬롯 점유 + 계좌 반환 + `dueAt` 설정을 원자적으로 수행한다. 별도 "계좌 보기" 단계 두지 않음.

---

## 9. 동시성 / CAS 설계 요약

| 상황 | 가드 |
|---|---|
| 동시 참여(같은 멤버) | BuncheolMember 점유 CAS (`status='AVAILABLE'`) — 1명만 성공 |
| 호스트 확인 vs 30분 만료 race | participation status CAS. 먼저 이긴 쪽 승리. 진 쪽은 `*_CONFLICT` |
| 마감 스케줄러 중복 실행 | Buncheol status CAS (`status='RECRUITING'`) |
| 만료 스케줄러 중복 | participation status CAS (`AWAITING_PAYMENT` + `due_at<=now`) |

### 호스트 확인 시 dueAt 검사 — **엄격 컷(확정)**
- 30분은 적극 참여자에겐 충분하므로 **칼같이 컷**한다. `confirmPayment`은 `status='AWAITING_PAYMENT' AND now <= due_at` 둘 다 가드.
  - `due_at` 경과 후 호스트가 확인 시도 → `PARTICIPATION_PAYMENT_DUE_PASSED`(신규).
  - 만료 스케줄러가 먼저 CAS로 취소했으면 → `*_CONFLICT`.
- 유예 없음. 입금이 늦으면 참여자가 다시 참여(재선착)해야 한다.

---

## 10. 제거 대상 인벤토리 (MVP 레포)

### 도메인/모듈 통째 제거
- `buncheoleasy.payment.**` — Payment, PaymentStatus, PaymentTxType, PaymentDomainService, JpaPaymentRepository(+Adapter), PaymentController, Toss 연동/DTO 전부

### 경매/입찰 로직 제거
- `ParticipationDomainService`: `selectWinners`, `expireWinnerAndPromoteNext`, 차순위 승계
- `Participation`: `awardAsWinner`, `assignClosedRank`, `markNotSelected`, `promoteToWinner`, `completePayment`, `reportPayment`, `rejectPayment`, `fail`, `expireUnpaid`, `cancel(현행)` → §3 메서드로 재작성
- `JpaParticipationRepository`: `updateStatusIfMatches`(복합) 단순화, `cancelByBuncheolIdAndStatusIn` 은 분철취소 일괄취소로 유지/조정
- `BuncheolMember.validateBidAmount`, `bidMinPrice`
- `ParticipationWonEvent` + 핸들러, `BuncheolAutoCloseScheduler`의 selectWinners 호출
- `PaymentDueReminderScheduler`, `DueReminderGuard`
- 입금기한 임박 알림(PG era) 경로

### 스키마
- `schema.sql` / `schema-test.sql`: payments 테이블 제거, participations 컬럼 교체(bid_amount/closed_rank/fail_reason/payment_reported_at 제거 → amount/refund_*/cancel_reason 추가), buncheols.min_headcount 추가, buncheol_members.bid_min_price→price + status/occupied_participation_id 추가

---

## 11. ErrorCode 변경

| 작업 | 코드 | 용도 |
|---|---|---|
| ADD | `INVALID_PRICE_UNIT` | 가격이 100원 단위 아님 |
| ADD | `PARTICIPATION_SLOT_TAKEN` (*_CONFLICT 계열) | 선착순 점유 실패 |
| ADD | `BUNCHEOL_MIN_HEADCOUNT_INVALID` | minHeadcount < 1 |
| ADD | `PARTICIPATION_PAYMENT_DUE_PASSED` | 30분/마감 경과 후 호스트 확인 시도(칼컷) |
| ADD | `PARTICIPATION_HOST_CANNOT_PARTICIPATE` | 호스트가 본인 분철 참여 시도 |
| KEEP | `*_CONFLICT`(확인 vs 만료 race), `*_NO_PERMISSION`, `BUNCHEOL_NOT_RECRUITING` | |
| REMOVE | `PARTICIPATION_BID_AMOUNT_INVALID`, `BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID`, `PARTICIPATION_PAYMENT_DUE_PASSED`, `PARTICIPATION_PAYMENT_NOT_DUE_YET` 등 경매/PG 전용 | |

> 발생 불가 시나리오엔 방어 로직 자체를 제거(메모리 원칙). 신규 코드는 `IllegalStateException` 금지, `BusinessException + ErrorCode` 만.

---

## 12. 엣지케이스 & 운영 주의

1. **마감 직전 참여 (확정)**: 참여 시 `dueAt = min(now+30분, deadline)` 로 **deadline에 클램프**. deadline 5분 전 참여자는 30분이 아니라 5분만 주어지며, 그 안에 입금+호스트확인이 끝나야 한다. deadline 후로 타이머가 넘어가지 않음. → UX상 "마감 임박 시 참여 위험" 안내 권장.
2. **30분/마감 경과는 칼같이 컷 (확정)**: 호스트가 입금받고도 기한 내 확인 못 누르면 참여자 컷오프 → 오프라인 환불. 운영자 교육 필요. 유예 없음.
3. **분철 취소 후 확정참여 환불**: 앱 미추적. 호스트 관리화면에 환불계좌 노출로 운영자가 수동 처리.
4. **호스트 본인 참여 (확정)**: **불가**. 자기 분철 참여 시도 차단(`PARTICIPATION_HOST_CANNOT_PARTICIPATE` 신규).
5. **중복 참여**: 같은 분철 내 서로 다른 멤버 다중 참여는 허용, 같은 멤버 중복은 슬롯 CAS로 자연 차단.
6. **타임존**: deadline/dueAt 모두 Instant(UTC) 저장, 표시 KST.

---

## 13. 미해결 / 추후 확장 (이번 범위 제외)

- 환불 상태 추적(REFUND_PENDING/REFUNDED) + 알림톡 — 일반 유저 개최 도입 시
- PG(Toss) 재도입, 경매/입찰 — 별도 레포 라인에서 유지
- **운송장 트래킹 API 연동** → 배송 완료 자동 종결 시 `Buncheol.FINISHED` 추가 (예정)
- 입금 임박(예: 5분 전) 리마인더 알림

---

## 14. 구현 순서 제안 (승인 후)

1. ErrorCode / Price VO / BankAccount 재사용 정리
2. BuncheolStatus·ParticipationStatus·cancelReason enum 재정의 + 엔티티 필드 교체
3. BuncheolMember 점유 모델 + 점유/반환 CAS
4. participate(점유+INSERT) / confirm / cancel 도메인·서비스·CAS 쿼리
5. 마감 스케줄러 개편(판정), 입금만료 스케줄러 신설
6. Payment 도메인 및 경매 잔재 제거 + 스키마/테스트 정리
7. 컨트롤러·DTO·REST Docs/OpenAPI 갱신
8. 단위/어댑터/스케줄러 테스트

각 단계는 작업 단위 완료 시 `code-quality-reviewer` 리뷰 후 진행.

---

## 결정 확정 (전 항목 종결)

- [x] §9 호스트 확인 시 `dueAt` **엄격 컷**(유예 없음, 30분 칼컷)
- [x] §12-1 참여 시 `dueAt = min(now+30분, deadline)` **deadline 클램프**(타이머가 deadline 못 넘음)
- [x] §12-4 호스트 본인 참여 **차단**
- [x] §2 `Buncheol.FINISHED` **제외** → 추후 운송장 트래킹 API 연동 시 추가
- [x] 워딩(입금확인중/진행확정/참여확정 등)은 **실제 진행 담당자에게 위임** — 본 문서 표기는 placeholder
