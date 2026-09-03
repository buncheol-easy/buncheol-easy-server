package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 이 클래스가 선언한 불변식 — <b>"묶음을 열었으면 반드시 연결한다"</b> — 을 검증한다.
 *
 * <p>서비스 테스트는 이 빈을 목으로 대체하고 호출 인자만 보므로, 열기→연결의 <b>순서</b>와 연결 실패 시 <b>예외로 롤백</b>되는지는
 * 여기서만 확인된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationBundleDomainService 단위 테스트")
class ParticipationBundleDomainServiceTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Long PARTICIPANT_ID = 100L;
  private static final Long PARTICIPATION_ID = 500L;
  private static final Long SHIPPING_ADDRESS_ID = 200L;
  private static final Long NEW_BUNDLE_ID = 900L;
  private static final Long EXISTING_BUNDLE_ID = 700L;
  private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");
  private static final RefundAccount REFUND_ACCOUNT =
      RefundAccount.of("국민", "12345678", "홍길동");

  @InjectMocks private ParticipationBundleDomainService participationBundleDomainService;

  @Mock private ParticipationBundleRepository participationBundleRepository;
  @Mock private ParticipationRepository participationRepository;

  private Participation participation() {
    Participation participation = newInstance(Participation.class);
    setField(participation, "id", PARTICIPATION_ID);
    setField(participation, "buncheolId", BUNCHEOL_ID);
    setField(participation, "participantId", PARTICIPANT_ID);
    return participation;
  }

  private void givenBundleSaved() {
    given(participationBundleRepository.save(any()))
        .willAnswer(
            invocation -> {
              ParticipationBundle bundle = invocation.getArgument(0);
              setField(bundle, "id", NEW_BUNDLE_ID);
              return bundle;
            });
  }

  private void attach(final Participation participation, final Long reusableBundleId) {
    participationBundleDomainService.attach(
        participation,
        reusableBundleId,
        SHIPPING_ADDRESS_ID,
        3000L,
        REFUND_ACCOUNT,
        null,
        NOW);
  }

  @Test
  void 재사용_후보가_없으면_묶음을_열고_그_id로_연결한다() {
    Participation participation = participation();
    givenBundleSaved();
    given(participationRepository.linkBundle(PARTICIPATION_ID, NEW_BUNDLE_ID, NOW))
        .willReturn(true);

    attach(participation, null);

    assertThat(participation.getBundleId()).isEqualTo(NEW_BUNDLE_ID);
    // 열기가 연결보다 먼저다 — 반대면 연결할 id 가 없다.
    InOrder inOrder = Mockito.inOrder(participationBundleRepository, participationRepository);
    inOrder.verify(participationBundleRepository).save(any());
    inOrder.verify(participationRepository).linkBundle(PARTICIPATION_ID, NEW_BUNDLE_ID, NOW);
  }

  @Test
  void 재사용_후보가_있으면_묶음을_새로_열지_않는다() {
    Participation participation = participation();
    given(participationRepository.linkBundle(PARTICIPATION_ID, EXISTING_BUNDLE_ID, NOW))
        .willReturn(true);

    attach(participation, EXISTING_BUNDLE_ID);

    assertThat(participation.getBundleId()).isEqualTo(EXISTING_BUNDLE_ID);
    then(participationBundleRepository).should(never()).save(any());
  }

  // 연결 결과를 메모리에도 반영해야 같은 트랜잭션의 뒤 코드(배송 스냅샷)가 묶음을 볼 수 있다.
  @Test
  void 연결에_성공하면_참여_객체에도_묶음_id가_반영된다() {
    Participation participation = participation();
    givenBundleSaved();
    given(participationRepository.linkBundle(anyLong(), anyLong(), any())).willReturn(true);

    attach(participation, null);

    assertThat(participation.getBundleId()).isEqualTo(NEW_BUNDLE_ID);
  }

  // 🔴 조용히 넘어가면 "참여는 있는데 묶음이 없는" 행이 남고, P4 의 NOT NULL 승격에서야 발견된다.
  @Test
  void 연결에_실패하면_예외를_던져_트랜잭션을_되돌린다() {
    Participation participation = participation();
    givenBundleSaved();
    given(participationRepository.linkBundle(anyLong(), anyLong(), any())).willReturn(false);

    assertThatThrownBy(() -> attach(participation, null))
        .isInstanceOf(BusinessException.class);
    assertThat(participation.getBundleId()).isNull();
  }

  @Test
  void 묶음이_없는_참여는_종료_판정을_건너뛴다() {
    participationBundleDomainService.closeIfEmpty(null, NOW);

    then(participationBundleRepository).should(never()).closeIfNoActiveSlots(any(), any());
  }

  @Test
  void 묶음이_있으면_종료_판정을_저장소에_위임한다() {
    participationBundleDomainService.closeIfEmpty(EXISTING_BUNDLE_ID, NOW);

    then(participationBundleRepository).should().closeIfNoActiveSlots(eq(EXISTING_BUNDLE_ID), eq(NOW));
  }

  private static <T> T newInstance(final Class<T> type) {
    try {
      Constructor<T> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
  // 🔴 어드민 결제 목록은 커서 페이지네이션이라 한 묶음의 슬롯이 페이지 경계로 쪼개진다.
  // 페이지 조각을 그대로 판정에 넘기면 그 안에서 carrier 를 다시 뽑아 배송비가 두 번 걷힌다 —
  // 이 진입점이 형제 슬롯을 대신 읽어 그걸 막는다.
  @Test
  @DisplayName("불완전한 목록을 받아도 형제 슬롯을 읽어 이중 부과를 막는다")
  void 배송비_귀속은_형제_슬롯을_대신_읽는다() {
    final Long bundleId = 141L;
    Participation carrier = participationFixture(232L, bundleId, 3_000L);
    Participation other = participationFixture(233L, bundleId, 0L);
    ParticipationBundle bundle = Mockito.mock(ParticipationBundle.class);
    given(bundle.getId()).willReturn(bundleId);
    given(bundle.getShippingFee()).willReturn(3_000L);
    // 형제 슬롯 전건을 돌려준다 — 호출부는 233 하나만 넘겼다.
    given(participationRepository.findAllByBundleIds(List.of(bundleId)))
        .willReturn(List.of(carrier, other));
    given(participationBundleRepository.findAllByIds(List.of(bundleId)))
        .willReturn(List.of(bundle));

    ShippingFeeAttribution attribution =
        participationBundleDomainService.shippingFeeAttributionFor(List.of(other));

    // 233 만 보였지만 carrier 는 232 다 — 조각만 봤다면 233 이 3,000 을 또 걷었을 것이다.
    assertThat(attribution.shippingFeeOf(other)).isZero();
    assertThat(attribution.shippingFeeOf(carrier)).isEqualTo(3_000L);
  }

  @Test
  @DisplayName("묶음 없는 참여만 있으면 조회 없이 빈 판정을 준다")
  void 미연결_참여만_있으면_조회하지_않는다() {
    Participation unlinked = participationFixture(300L, null, 3_000L);

    ShippingFeeAttribution attribution =
        participationBundleDomainService.shippingFeeAttributionFor(List.of(unlinked));

    assertThat(attribution.shippingFeeOf(unlinked)).isEqualTo(3_000L);
    then(participationRepository).should(never()).findAllByBundleIds(any());
  }

  @Nested
  @DisplayName("shippingAddressIdOf — 배송지 정본 읽기 규칙")
  class ShippingAddressIdOfTest {

    private static final Long BUNDLE_ID = 900L;
    private static final Long BUNDLE_ADDRESS_ID = 201L;
    private static final Long STALE_COPY_ADDRESS_ID = 999L;

    @Test
    void 묶음의_배송지를_돌려준다() {
      Participation participation = linked();
      given(participationBundleRepository.findById(BUNDLE_ID))
          .willReturn(Optional.of(bundle(BUNDLE_ADDRESS_ID)));

      assertThat(participationBundleDomainService.shippingAddressIdOf(participation))
          .isEqualTo(BUNDLE_ADDRESS_ID);
    }

    // 🔴 이 PR 의 핵심 규칙. 묶음 주소가 NULL 인 것은 <b>참조 배송지가 삭제됐다</b>는 뜻이고,
    // 사본의 id 는 이미 없는 행을 가리킨다 — 되살리면 FK 위반이다.
    //
    // ⚠️ Optional.map(...).orElseGet(...) 으로 구현하면 매퍼가 null 을 줄 때 empty 가 되어
    // <b>정확히 이 금지된 폴백</b>을 한다. 이 테스트가 그것을 막는다.
    @Test
    void 묶음_주소가_NULL_이면_사본으로_폴백하지_않고_NULL_을_돌려준다() {
      Participation participation = linked();
      given(participationBundleRepository.findById(BUNDLE_ID))
          .willReturn(Optional.of(bundle(null)));

      assertThat(participationBundleDomainService.shippingAddressIdOf(participation)).isNull();
    }

    // 미연결 옛 행(P2-b 배포선 창)만 사본으로 폴백한다. P4 의 bundle_id NOT NULL 승격과 함께 사라진다.
    @Test
    void 묶음이_없으면_참여_사본으로_폴백한다() {
      Participation participation =
          participationFixture(310L, null, 0L);
      setField(participation, "shippingAddressId", STALE_COPY_ADDRESS_ID);

      assertThat(participationBundleDomainService.shippingAddressIdOf(participation))
          .isEqualTo(STALE_COPY_ADDRESS_ID);
      then(participationBundleRepository).should(never()).findById(any());
    }

    // 🔴 참여 INSERT 에서 배송지를 뺀 뒤(#175) null 이 실제로 흐를 수 있게 된다.
    // 읽기 쪽은 findById(null) 이 안내 없는 500 으로 죽고, <b>쓰기 쪽은 배송지가 NULL 인 새 묶음이
    // 조용히 커밋된다</b>(updatable=false 라 코드로 복구 불가) — 뒤쪽이 더 비가역이라 두 호출부가
    // 같은 규약을 쓰게 모아 뒀다.
    @Test
    void 정본_배송지가_없으면_이름을_가진_예외로_닫는다() {
      Participation participation = linked();
      given(participationBundleRepository.findById(BUNDLE_ID))
          .willReturn(Optional.of(bundle(null)));

      assertThatThrownBy(
              () -> participationBundleDomainService.requireShippingAddressIdOf(participation))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);
    }

    @Test
    void 정본_배송지가_있으면_그대로_돌려준다() {
      Participation participation = linked();
      given(participationBundleRepository.findById(BUNDLE_ID))
          .willReturn(Optional.of(bundle(BUNDLE_ADDRESS_ID)));

      assertThat(participationBundleDomainService.requireShippingAddressIdOf(participation))
          .isEqualTo(BUNDLE_ADDRESS_ID);
    }

    private Participation linked() {
      Participation participation = participationFixture(311L, BUNDLE_ID, 0L);
      // 사본에는 다른 값을 심는다 — 사본을 읽으면 위 두 테스트가 실패한다.
      setField(participation, "shippingAddressId", STALE_COPY_ADDRESS_ID);
      return participation;
    }

    private ParticipationBundle bundle(final Long shippingAddressId) {
      ParticipationBundle bundle = newInstance(ParticipationBundle.class);
      setField(bundle, "id", BUNDLE_ID);
      setField(bundle, "shippingAddressId", shippingAddressId);
      return bundle;
    }
  }

  private static Participation participationFixture(
      final Long id, final Long bundleId, final long shippingFee) {
    final Long buncheolMemberId = 500L + id;
    Participation participation =
        Participation.createApplied(104L, buncheolMemberId, 10L, 1L, 10_000L, shippingFee);
    setField(participation, "id", id);
    setField(participation, "bundleId", bundleId);
    setField(participation, "status", ParticipationStatus.APPLIED);
    setField(participation, "createdAt", Instant.parse("2026-08-29T00:00:00Z").plusSeconds(id));
    return participation;
  }

}
