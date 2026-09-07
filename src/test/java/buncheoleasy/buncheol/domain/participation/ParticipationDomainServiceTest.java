package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationDomainService 단위 테스트")
class ParticipationDomainServiceTest {

  private static final Long PARTICIPATION_ID = 500L;
  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");
  private static final RefundAccount REFUND_ACCOUNT = RefundAccount.of("국민", "12345678", "홍길동");
  private static final Instant DUE_AT = Instant.parse("2026-05-14T12:30:00Z");

  @InjectMocks private ParticipationDomainService participationDomainService;

  @Mock private ParticipationRepository participationRepository;

  private static Participation newParticipation() {
    return Participation.create(1L, 10L, 100L, 200L, 30_000L, 0L, DUE_AT);
  }

  private static Buncheol buncheol(final boolean c2c, final BuncheolStatus status) {
    Buncheol buncheol = mock(Buncheol.class);
    lenient().when(buncheol.getId()).thenReturn(10L);
    lenient().when(buncheol.isC2c()).thenReturn(c2c);
    lenient().when(buncheol.getStatus()).thenReturn(status);
    return buncheol;
  }

  @Nested
  @DisplayName("상속 원본 판정 테스트")
  class FindInheritanceSourceTest {

    // 🔴 이 판정 하나를 쓰기 경로(participateSingle)와 읽기 경로(분철 상세 응답)가 공유한다.
    // 조건을 두 벌 세우면 화면이 "이 배송지로 갑니다" 라고 약속한 주소와 서버가 실제로 각인하는 주소가
    // 갈리고, 배송지는 updatable=false 라 되돌릴 수 없다. 그래서 게이트를 여기서 검증한다.
    @Test
    void C2C_모집중이면_내_첫_활성_참여를_상속_원본으로_준다() {
      Participation mine = newParticipation();
      given(participationRepository.findActiveByBuncheolId(10L)).willReturn(List.of(mine));

      assertThat(
              participationDomainService.findInheritanceSource(
                  buncheol(true, BuncheolStatus.RECRUITING), mine.getParticipantId()))
          .contains(mine);
    }

    // 🔴 게이트가 둘인데 하나(RECRUITING)만 넣었다가 실제로 터진 버그다. LEGACY 는 1인 1활성슬롯이라
    // 묶음 재사용이 구조적으로 없고(attach(.., null, ..)) 추가 신청 자체가 막히는데, 상속 원본을 주면
    // 화면이 「이 주소로 함께 배송 · 변경 불가」를 그린다 — 서버에 그 약속을 이행할 경로가 없다.
    @Test
    void LEGACY_는_모집중이어도_상속하지_않는다() {
      assertThat(
              participationDomainService.findInheritanceSource(
                  buncheol(false, BuncheolStatus.RECRUITING), 100L))
          .isEmpty();

      then(participationRepository).shouldHaveNoInteractions();
    }

    // 이미 읽어 둔 목록으로 판정하는 오버로드도 <b>같은</b> 게이트를 통과해야 한다.
    // 게이트를 한쪽에만 넣으면 조회를 아끼려다 판정이 갈린다.
    @Test
    void 목록을_넘겨받는_경로도_같은_게이트를_쓴다() {
      Participation mine = newParticipation();

      assertThat(
              participationDomainService.findInheritanceSource(
                  buncheol(true, BuncheolStatus.RECRUITING), mine.getParticipantId(), List.of(mine)))
          .contains(mine);
      assertThat(
              participationDomainService.findInheritanceSource(
                  buncheol(false, BuncheolStatus.RECRUITING), mine.getParticipantId(), List.of(mine)))
          .isEmpty();
      assertThat(
              participationDomainService.findInheritanceSource(
                  buncheol(true, BuncheolStatus.PAYMENT_COLLECTING),
                  mine.getParticipantId(),
                  List.of(mine)))
          .isEmpty();
      // 조회는 한 번도 나가지 않는다 — 이 오버로드의 존재 이유다.
      then(participationRepository).shouldHaveNoInteractions();
    }

    // 성사 확정 뒤 추가 모집은 별도 이체·별도 택배다 (docs/80 결정 11).
    // 🔴 <b>조회 자체를 하지 않는다</b> — 추가 모집에서 상속은 구조적으로 불가능하므로 헛쿼리다.
    @Test
    void 추가_모집이면_조회도_하지_않고_비어_있다() {
      assertThat(
              participationDomainService.findInheritanceSource(
                  buncheol(true, BuncheolStatus.PAYMENT_COLLECTING), 100L))
          .isEmpty();

      then(participationRepository).shouldHaveNoInteractions();
    }

    @Test
    void 모집중이어도_내_활성_참여가_없으면_비어_있다() {
      given(participationRepository.findActiveByBuncheolId(10L))
          .willReturn(List.of(newParticipation()));

      assertThat(
              participationDomainService.findInheritanceSource(
                  buncheol(true, BuncheolStatus.RECRUITING), 999L))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("참여 생성 테스트")
  class CreateParticipationIfRecruitingTest {

    @Test
    void 모집중인_분철에_참여_생성에_성공한다() {
      Participation participation = newParticipation();
      given(participationRepository.saveIfRecruiting(participation)).willReturn(true);

      boolean result = participationDomainService.createParticipationIfRecruiting(participation);

      assertThat(result).isTrue();
    }

    @Test
    void 모집중이_아닌_분철이면_false를_반환한다() {
      Participation participation = newParticipation();
      given(participationRepository.saveIfRecruiting(participation)).willReturn(false);

      boolean result = participationDomainService.createParticipationIfRecruiting(participation);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("참여 조회 테스트")
  class GetParticipationTest {

    @Test
    void 존재하는_참여를_조회한다() {
      Participation participation = newParticipation();
      given(participationRepository.findById(PARTICIPATION_ID))
          .willReturn(Optional.of(participation));

      Participation result = participationDomainService.getParticipation(PARTICIPATION_ID);

      assertThat(result).isSameAs(participation);
    }

    @Test
    void 존재하지_않는_참여를_조회하면_예외가_발생한다() {
      given(participationRepository.findById(999L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> participationDomainService.getParticipation(999L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("분철별 참여 조회/집계 위임 테스트")
  class FindByBuncheolIdTest {

    @Test
    void 활성_참여_조회를_리포지토리에_위임한다() {
      Participation participation = newParticipation();
      given(participationRepository.findActiveByBuncheolId(1L))
          .willReturn(List.of(participation));

      List<Participation> result = participationDomainService.findActiveByBuncheolId(1L);

      assertThat(result).containsExactly(participation);
    }

    @Test
    void 입금확인된_참여_조회를_리포지토리에_위임한다() {
      Participation participation = newParticipation();
      given(participationRepository.findConfirmedByBuncheolId(1L))
          .willReturn(List.of(participation));

      List<Participation> result = participationDomainService.findConfirmedByBuncheolId(1L);

      assertThat(result).containsExactly(participation);
    }

    @Test
    void 입금확인된_참여_수_집계를_리포지토리에_위임한다() {
      given(participationRepository.countConfirmedByBuncheolId(1L)).willReturn(3);

      assertThat(participationDomainService.countConfirmedByBuncheolId(1L)).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("참여자의 끝나지 않은 참여 존재 여부 테스트")
  class HasUnfinishedParticipationByTest {

    @Test
    void 끝나지_않은_참여가_있으면_true를_반환한다() {
      given(participationRepository.existsUnfinishedByParticipantId(100L)).willReturn(true);

      assertThat(participationDomainService.hasUnfinishedParticipationBy(100L)).isTrue();
    }

    @Test
    void 끝나지_않은_참여가_없으면_false를_반환한다() {
      given(participationRepository.existsUnfinishedByParticipantId(100L)).willReturn(false);

      assertThat(participationDomainService.hasUnfinishedParticipationBy(100L)).isFalse();
    }
  }

  @Nested
  @DisplayName("입금 만료 대상 조회 테스트")
  class FindOverduePaymentTargetsTest {

    @Test
    void 입금_만료_대상_조회를_리포지토리에_위임한다() {
      Participation participation = newParticipation();
      given(participationRepository.findOverduePaymentTargets(NOW, 200))
          .willReturn(List.of(participation));

      List<Participation> result =
          participationDomainService.findOverduePaymentTargets(NOW, 200);

      assertThat(result).containsExactly(participation);
    }
  }

  @Nested
  @DisplayName("호스트 입금확인 테스트")
  class ConfirmPaymentTest {

    @Test
    void CAS_성공시_별도_재조회_없이_통과한다() {
      given(participationRepository.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(true);

      participationDomainService.confirmPayment(PARTICIPATION_ID, NOW);

      then(participationRepository).should(never()).findById(PARTICIPATION_ID);
    }

    @Test
    void CAS_실패_후_여전히_입금확인중이면_기한_경과_예외를_던진다() {
      given(participationRepository.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(false);
      given(participationRepository.findById(PARTICIPATION_ID))
          .willReturn(Optional.of(awaitingParticipation()));

      assertThatThrownBy(() -> participationDomainService.confirmPayment(PARTICIPATION_ID, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_PAYMENT_DUE_PASSED);
    }

    @Test
    void CAS_실패_후_이미_취소_확정됐으면_상태_위반_예외를_던진다() {
      given(participationRepository.confirmPaymentIfAwaiting(PARTICIPATION_ID, NOW))
          .willReturn(false);
      given(participationRepository.findById(PARTICIPATION_ID))
          .willReturn(Optional.of(participationWithStatus(ParticipationStatus.CANCELLED)));

      assertThatThrownBy(() -> participationDomainService.confirmPayment(PARTICIPATION_ID, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  @Nested
  @DisplayName("입금 만료 처리 테스트")
  class ExpireOverduePaymentTest {

    @Test
    void CAS_성공시_true를_반환한다() {
      given(participationRepository.expirePaymentIfOverdue(PARTICIPATION_ID, NOW)).willReturn(true);

      assertThat(participationDomainService.expirePayment(PARTICIPATION_ID, NOW)).isTrue();
    }

    @Test
    void CAS_실패시_예외_없이_false를_반환한다() {
      given(participationRepository.expirePaymentIfOverdue(PARTICIPATION_ID, NOW)).willReturn(false);

      assertThat(participationDomainService.expirePayment(PARTICIPATION_ID, NOW)).isFalse();
    }
  }

  @Nested
  @DisplayName("분철 취소 cascade — 활성 참여 일괄 전이 테스트")
  class CancelActiveByBuncheolIdTest {

    @Test
    void 리포지토리에_그대로_위임하고_갱신_행_수를_반환한다() {
      given(participationRepository.cancelActiveByBuncheolId(1L, NOW)).willReturn(3);

      int affected = participationDomainService.cancelActiveByBuncheolId(1L, NOW);

      assertThat(affected).isEqualTo(3);
      then(participationRepository).should().cancelActiveByBuncheolId(1L, NOW);
    }
  }

  @Nested
  @DisplayName("배송비 환급 완료/반려 전이 테스트")
  class PaybackTransitionTest {

    @Test
    void 완료_CAS_성공이면_예외_없이_끝난다() {
      given(participationRepository.completePaybackIfRequested(PARTICIPATION_ID, NOW))
          .willReturn(true);

      participationDomainService.completePayback(PARTICIPATION_ID, NOW);

      then(participationRepository).should().completePaybackIfRequested(PARTICIPATION_ID, NOW);
    }

    @Test
    void 완료_CAS_실패_시_참여가_존재하면_상태_위반으로_구분한다() {
      given(participationRepository.completePaybackIfRequested(PARTICIPATION_ID, NOW))
          .willReturn(false);
      given(participationRepository.findById(PARTICIPATION_ID))
          .willReturn(Optional.of(newParticipation()));

      assertThatThrownBy(() -> participationDomainService.completePayback(PARTICIPATION_ID, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYBACK_STATE_TRANSITION_INVALID);
    }

    @Test
    void 완료_CAS_실패_시_참여가_없으면_404_로_구분한다() {
      given(participationRepository.completePaybackIfRequested(PARTICIPATION_ID, NOW))
          .willReturn(false);
      given(participationRepository.findById(PARTICIPATION_ID)).willReturn(Optional.empty());

      assertThatThrownBy(() -> participationDomainService.completePayback(PARTICIPATION_ID, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_NOT_FOUND);
    }

    @Test
    void 반려_CAS_성공이면_예외_없이_끝난다() {
      given(participationRepository.rejectPaybackIfRequested(PARTICIPATION_ID, "사유", NOW))
          .willReturn(true);

      participationDomainService.rejectPayback(PARTICIPATION_ID, "사유", NOW);

      then(participationRepository).should().rejectPaybackIfRequested(PARTICIPATION_ID, "사유", NOW);
    }

    @Test
    void 반려_사유가_비어있으면_CAS_없이_예외가_발생한다() {
      assertThatThrownBy(() -> participationDomainService.rejectPayback(PARTICIPATION_ID, "  ", NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYBACK_REJECT_REASON_REQUIRED);
      then(participationRepository)
          .should(never())
          .rejectPaybackIfRequested(PARTICIPATION_ID, "  ", NOW);
    }

    @Test
    void 반려_CAS_실패_시_참여가_존재하면_상태_위반으로_구분한다() {
      given(participationRepository.rejectPaybackIfRequested(PARTICIPATION_ID, "사유", NOW))
          .willReturn(false);
      given(participationRepository.findById(PARTICIPATION_ID))
          .willReturn(Optional.of(newParticipation()));

      assertThatThrownBy(
              () -> participationDomainService.rejectPayback(PARTICIPATION_ID, "사유", NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PAYBACK_STATE_TRANSITION_INVALID);
    }

    @Test
    void 반려_CAS_실패_시_참여가_없으면_404_로_구분한다() {
      given(participationRepository.rejectPaybackIfRequested(PARTICIPATION_ID, "사유", NOW))
          .willReturn(false);
      given(participationRepository.findById(PARTICIPATION_ID)).willReturn(Optional.empty());

      assertThatThrownBy(
              () -> participationDomainService.rejectPayback(PARTICIPATION_ID, "사유", NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_NOT_FOUND);
    }
  }

  private static Participation awaitingParticipation() {
    return participationWithStatus(ParticipationStatus.AWAITING_PAYMENT);
  }

  private static Participation participationWithStatus(final ParticipationStatus status) {
    Participation participation = newParticipation();
    setField(participation, "status", status);
    return participation;
  }

  private static void setField(final Object target, final String fieldName, final Object value) {
    try {
      Field field = Participation.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
