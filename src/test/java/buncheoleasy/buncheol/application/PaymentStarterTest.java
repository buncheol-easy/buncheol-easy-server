package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentStarter 단위 테스트 — 회원 개최 입금 단계 시작")
class PaymentStarterTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Long HOST_ID = 1L;
  private static final Instant NOW = Instant.parse("2026-11-05T03:00:00Z");
  private static final BankAccount HOST_ACCOUNT = BankAccount.of("국민", "98765432", "개최자");

  @InjectMocks private PaymentStarter paymentStarter;

  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private UserDomainService userDomainService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private Buncheol buncheol;

  @BeforeEach
  void setUp() {
    buncheol = mock(Buncheol.class);
    lenient().when(buncheol.getId()).thenReturn(BUNCHEOL_ID);
    lenient().when(buncheol.getHostId()).thenReturn(HOST_ID);
  }

  private void givenMinHeadcount(final int minHeadcount) {
    given(buncheol.getMinHeadcount()).willReturn(minHeadcount);
  }

  private void givenOpenSlots(final int open, final int closed) {
    List<BuncheolMember> slots = new ArrayList<>();
    for (int i = 0; i < open + closed; i++) {
      BuncheolMember slot = BuncheolMember.create(BUNCHEOL_ID, 100L + i, 10_000L);
      if (i >= open) {
        setField(slot, "accessType", BuncheolMemberAccessType.CLOSED);
      }
      slots.add(slot);
    }
    given(buncheolMemberDomainService.findAllByBuncheolIdForShare(BUNCHEOL_ID)).willReturn(slots);
  }

  private static Participation applied(final long id) {
    Participation participation = mock(Participation.class);
    lenient().when(participation.getId()).thenReturn(id);
    lenient().when(participation.getStatus()).thenReturn(ParticipationStatus.APPLIED);
    return participation;
  }

  private void givenActive(final Participation... participations) {
    given(participationDomainService.findActiveByBuncheolIdForUpdate(BUNCHEOL_ID))
        .willReturn(List.of(participations));
  }

  private void givenStartSucceeds(final int moved) {
    User host = mock(User.class);
    given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
    given(userDomainService.getUser(HOST_ID)).willReturn(host);
    given(
            buncheolDomainService.startCollecting(
                BUNCHEOL_ID, NOW.plus(Duration.ofHours(24)), HOST_ACCOUNT, NOW))
        .willReturn(true);
    given(
            participationDomainService.startPaymentCollecting(
                BUNCHEOL_ID, NOW.plus(Duration.ofHours(24)), NOW))
        .willReturn(moved);
  }

  @Nested
  @DisplayName("정원 충족(신청·자리 빼기)")
  class StartIfFull {

    @Test
    void 열린_자리가_모두_신청됐고_최소_인원_이상이면_입금_단계를_시작한다() {
      givenMinHeadcount(2);
      givenOpenSlots(2, 0);
      givenActive(applied(501L), applied(502L));
      givenStartSucceeds(2);

      assertThat(paymentStarter.startIfFull(buncheol, NOW)).isTrue();

      // 기한은 지금 + 24시간, 계좌는 개최자 정산 계좌 스냅샷 — 옛 성사 확정과 같은 칸을 채운다.
      then(buncheolDomainService)
          .should()
          .startCollecting(BUNCHEOL_ID, NOW.plus(Duration.ofHours(24)), HOST_ACCOUNT, NOW);
      // 신청 구간의 묶음은 기한 없이 열려 있다 — 같은 기한을 묶음에도 채운다.
      then(participationBundleDomainService)
          .should()
          .assignDueAtByBuncheolId(BUNCHEOL_ID, NOW.plus(Duration.ofHours(24)), NOW);
      // 커밋 시점에 입금 대기가 된 참여만 이벤트에 싣는다 — 리스너는 이 목록으로만 보낸다.
      then(eventPublisher)
          .should()
          .publishEvent(new PaymentStartedEvent(BUNCHEOL_ID, List.of(501L, 502L)));
    }

    // 개최자가 뺀(닫은) 자리는 채워질 수 없다 — 세면 남은 자리가 다 차도 영영 시작하지 않는다.
    @Test
    void 닫힌_자리는_세지_않는다() {
      givenMinHeadcount(1);
      givenOpenSlots(2, 1);
      givenActive(applied(501L), applied(502L));
      givenStartSucceeds(2);

      assertThat(paymentStarter.startIfFull(buncheol, NOW)).isTrue();
    }

    @Test
    void 열린_자리가_남아_있으면_시작하지_않는다() {
      givenOpenSlots(3, 0);
      givenActive(applied(501L), applied(502L));

      assertThat(paymentStarter.startIfFull(buncheol, NOW)).isFalse();

      then(buncheolDomainService).should(never()).startCollecting(anyLong(), any(), any(), any());
      then(eventPublisher).shouldHaveNoInteractions();
    }

    // 열린 자리가 최소 인원보다 적으면 다 차도 진행할 수 없다 — 마감 때 미달 취소된다.
    @Test
    void 열린_자리가_다_차도_최소_인원_미만이면_시작하지_않는다() {
      givenMinHeadcount(3);
      givenOpenSlots(2, 0);
      givenActive(applied(501L), applied(502L));

      assertThat(paymentStarter.startIfFull(buncheol, NOW)).isFalse();

      then(buncheolDomainService).should(never()).startCollecting(anyLong(), any(), any(), any());
    }

    // 🔴 판정은 잠금 읽기로만 한다 — 잠금 전 스냅샷으로 세면 기다리는 사이 커밋된 신청·취소·자리 빼기를 놓친다.
    @Test
    void 자리와_참여를_잠금_읽기로_센다() {
      givenOpenSlots(2, 0);
      givenActive(applied(501L));

      paymentStarter.startIfFull(buncheol, NOW);

      then(buncheolMemberDomainService).should().findAllByBuncheolIdForShare(BUNCHEOL_ID);
      then(buncheolMemberDomainService).should(never()).findAllByBuncheolId(anyLong());
      then(participationDomainService).should().findActiveByBuncheolIdForUpdate(BUNCHEOL_ID);
      then(participationDomainService).should(never()).findActiveByBuncheolId(anyLong());
    }
  }

  @Nested
  @DisplayName("마감")
  class StartAtDeadline {

    @Test
    void 신청_자리가_최소_인원_이상이면_빈_자리가_있어도_시작한다() {
      givenMinHeadcount(2);
      givenActive(applied(501L), applied(502L));
      givenStartSucceeds(2);

      assertThat(paymentStarter.startAtDeadline(buncheol, NOW)).isTrue();

      // 마감 판정은 빈 자리를 보지 않는다.
      then(buncheolMemberDomainService).shouldHaveNoInteractions();
      then(eventPublisher)
          .should()
          .publishEvent(new PaymentStartedEvent(BUNCHEOL_ID, List.of(501L, 502L)));
    }

    @Test
    void 최소_인원_미만이면_시작하지_않고_false_를_돌려준다() {
      givenMinHeadcount(3);
      givenActive(applied(501L), applied(502L));

      assertThat(paymentStarter.startAtDeadline(buncheol, NOW)).isFalse();

      then(buncheolDomainService).should(never()).startCollecting(anyLong(), any(), any(), any());
      then(participationDomainService).should(never()).startPaymentCollecting(anyLong(), any(), any());
    }
  }

  @Nested
  @DisplayName("전이")
  class Transition {

    // 이미 입금 수집 중이거나 취소된 분철 — CAS 가 0행이면 참여·묶음·알림을 건드리지 않는다.
    @Test
    void 분철_CAS_가_실패하면_참여를_넘기지_않고_false() {
      givenMinHeadcount(1);
      givenActive(applied(501L));
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(HOST_ACCOUNT);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);
      given(buncheolDomainService.startCollecting(anyLong(), any(), any(), any())).willReturn(false);

      assertThat(paymentStarter.startAtDeadline(buncheol, NOW)).isFalse();

      then(participationDomainService).should(never()).startPaymentCollecting(anyLong(), any(), any());
      then(participationBundleDomainService)
          .should(never())
          .assignDueAtByBuncheolId(anyLong(), any(), any());
      then(eventPublisher).shouldHaveNoInteractions();
    }

    // 참여 행을 잠근 채 셌는데 넘어간 수가 다르면 내부 불변식 위반이다 — 롤백해 분철만 넘어간 상태를 남기지 않는다.
    @Test
    void 넘어간_참여_수가_센_신청_수와_다르면_롤백한다() {
      givenMinHeadcount(1);
      givenActive(applied(501L), applied(502L));
      givenStartSucceeds(1);

      assertThatThrownBy(() -> paymentStarter.startAtDeadline(buncheol, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);

      then(eventPublisher).shouldHaveNoInteractions();
    }

    // 정산 계좌는 지우는 API 가 없어 비어 있을 수 없다 — 비었다면 계좌 없는 입금 안내를 보내지 않고 롤백한다.
    @Test
    void 개최자_계좌가_비어_있으면_시작하지_않고_롤백한다() {
      givenMinHeadcount(1);
      givenActive(applied(501L));
      User host = mock(User.class);
      given(host.getBankAccount()).willReturn(null);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);

      assertThatThrownBy(() -> paymentStarter.startAtDeadline(buncheol, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);

      then(buncheolDomainService).should(never()).startCollecting(anyLong(), any(), any(), any());
    }
  }
}
