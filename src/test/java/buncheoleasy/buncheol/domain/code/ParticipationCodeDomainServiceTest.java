package buncheoleasy.buncheol.domain.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.time.Duration;
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
import org.springframework.util.ReflectionUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationCodeDomainService 단위 테스트")
class ParticipationCodeDomainServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
  private static final Long SLOT_ID = 101L;
  private static final Long BUNCHEOL_ID = 10L;
  private static final String RAW_CODE = "abcd-2345";
  private static final String NORMALIZED_CODE = "ABCD2345";

  @InjectMocks private ParticipationCodeDomainService participationCodeDomainService;

  @Mock private ParticipationCodeRepository participationCodeRepository;
  @Mock private CodeGenerator codeGenerator;

  private BuncheolMember member(final BuncheolMemberAccessType accessType) {
    BuncheolMember member = newInstance();
    setField(member, "id", SLOT_ID);
    setField(member, "buncheolId", BUNCHEOL_ID);
    setField(member, "memberId", 1001L);
    setField(member, "price", 0L);
    setField(member, "accessType", accessType);
    return member;
  }

  private ParticipationCode code(final Long slotId) {
    ParticipationCode code =
        ParticipationCode.issue(
            NORMALIZED_CODE, BUNCHEOL_ID, slotId, null, NOW.plus(Duration.ofHours(48)), NOW);
    setField(code, "id", 7L);
    return code;
  }

  private static BuncheolMember newInstance() {
    try {
      var constructor = BuncheolMember.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void setField(final Object target, final String name, final Object value) {
    Field field = ReflectionUtils.findField(target.getClass(), name);
    ReflectionUtils.makeAccessible(field);
    ReflectionUtils.setField(field, target, value);
  }

  @Nested
  @DisplayName("참여 시 코드 검증 테스트")
  class ValidateForParticipationTest {

    @Test
    void 선착순_슬롯에_코드가_없으면_소모할_코드_없이_통과한다() {
      assertThat(
              participationCodeDomainService.validateForParticipation(
                  member(BuncheolMemberAccessType.OPEN), null, NOW))
          .isEmpty();
    }

    @Test
    void 선착순_슬롯에_코드를_보내면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  participationCodeDomainService.validateForParticipation(
                      member(BuncheolMemberAccessType.OPEN), RAW_CODE, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_NOT_APPLICABLE);
    }

    @Test
    void 코드_슬롯에_코드가_없으면_예외가_발생한다() {
      assertThatThrownBy(
              () ->
                  participationCodeDomainService.validateForParticipation(
                      member(BuncheolMemberAccessType.CODE_ONLY), "  ", NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_REQUIRED);
    }

    @Test
    void 정규화한_코드로_조회한다() {
      given(participationCodeRepository.findByCode(NORMALIZED_CODE))
          .willReturn(Optional.of(code(SLOT_ID)));

      assertThat(
              participationCodeDomainService.validateForParticipation(
                  member(BuncheolMemberAccessType.CODE_ONLY), RAW_CODE, NOW))
          .isPresent();
    }

    @Test
    void 존재하지_않는_코드면_형식_오류와_같은_코드로_응답한다() {
      given(participationCodeRepository.findByCode(NORMALIZED_CODE)).willReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  participationCodeDomainService.validateForParticipation(
                      member(BuncheolMemberAccessType.CODE_ONLY), RAW_CODE, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_INVALID);
    }

    // 타 슬롯임을 알리면 남의 코드를 받은 사람이 그 슬롯을 찾아가 점유한다 — 미존재 코드와 같은 응답으로 덮는다.
    @Test
    void 다른_슬롯의_코드면_미존재_코드와_같은_에러로_응답한다() {
      given(participationCodeRepository.findByCode(NORMALIZED_CODE))
          .willReturn(Optional.of(code(999L)));

      assertThatThrownBy(
              () ->
                  participationCodeDomainService.validateForParticipation(
                      member(BuncheolMemberAccessType.CODE_ONLY), RAW_CODE, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_INVALID);
    }

    @Test
    void 기한이_지난_코드면_만료_예외가_발생한다() {
      given(participationCodeRepository.findByCode(NORMALIZED_CODE))
          .willReturn(Optional.of(code(SLOT_ID)));

      assertThatThrownBy(
              () ->
                  participationCodeDomainService.validateForParticipation(
                      member(BuncheolMemberAccessType.CODE_ONLY),
                      RAW_CODE,
                      NOW.plus(Duration.ofHours(48))))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_EXPIRED);
    }
  }

  @Nested
  @DisplayName("코드 소모 테스트")
  class ConsumeTest {

    @Test
    void CAS_에_실패하면_이미_사용됨_예외가_발생한다() {
      ParticipationCode code = code(SLOT_ID);
      given(participationCodeRepository.markUsedIfRedeemable(code.getId(), 500L, NOW))
          .willReturn(false);

      assertThatThrownBy(() -> participationCodeDomainService.consume(code, 500L, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_ALREADY_USED);
    }
  }

  @Nested
  @DisplayName("발급·재발급 테스트")
  class IssueTest {

    @Test
    void 선착순_슬롯에는_발급할_수_없다() {
      assertThatThrownBy(
              () ->
                  participationCodeDomainService.issue(
                      member(BuncheolMemberAccessType.OPEN), null, NOW.plus(Duration.ofHours(48)), NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_MEMBER_NOT_CODE_ONLY);

      then(participationCodeRepository).should(never()).save(any());
    }

    // DB 유니크를 걷어낸 자리를 이 가드가 대신한다.
    @Test
    void 슬롯에_아직_쓸_수_있는_코드가_있으면_발급을_거부한다() {
      given(participationCodeRepository.findOutstandingByBuncheolMemberId(SLOT_ID))
          .willReturn(List.of(code(SLOT_ID)));

      assertThatThrownBy(
              () ->
                  participationCodeDomainService.issue(
                      member(BuncheolMemberAccessType.CODE_ONLY),
                      "@next",
                      NOW.plus(Duration.ofHours(48)),
                      NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_MEMBER_ALREADY_ISSUED);

      then(participationCodeRepository).should(never()).save(any());
    }

    @Test
    void 만료된_코드만_있으면_폐기_없이_발급된다() {
      given(participationCodeRepository.findOutstandingByBuncheolMemberId(SLOT_ID))
          .willReturn(List.of(code(SLOT_ID)));
      given(codeGenerator.generate()).willReturn(new CodeText("ZZZZ9999"));
      given(participationCodeRepository.save(any())).willAnswer(it -> it.getArgument(0));

      Instant afterExpiry = NOW.plus(Duration.ofHours(49));
      participationCodeDomainService.issue(
          member(BuncheolMemberAccessType.CODE_ONLY), "@next", afterExpiry.plusSeconds(60), afterExpiry);

      then(participationCodeRepository).should().save(any());
      then(participationCodeRepository)
          .should(never())
          .revokeOutstandingByBuncheolMemberId(any(), any());
    }

    // 순서가 뒤집히면 방금 발급한 코드까지 폐기된다.
    @Test
    void 재발급은_남은_코드를_모두_폐기한_뒤_저장한다() {
      given(codeGenerator.generate()).willReturn(new CodeText("ZZZZ9999"));
      given(participationCodeRepository.save(any())).willAnswer(it -> it.getArgument(0));

      participationCodeDomainService.reissue(
          member(BuncheolMemberAccessType.CODE_ONLY), "@next", NOW.plus(Duration.ofHours(48)), NOW);

      InOrder inOrder = Mockito.inOrder(participationCodeRepository);
      inOrder.verify(participationCodeRepository).revokeOutstandingByBuncheolMemberId(SLOT_ID, NOW);
      inOrder.verify(participationCodeRepository).save(any());
    }
  }

  @Nested
  @DisplayName("폐기 테스트")
  class RevokeTest {

    @Test
    void 존재하지_않는_코드면_예외가_발생한다() {
      given(participationCodeRepository.findById(7L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> participationCodeDomainService.revoke(7L, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_NOT_FOUND);
    }

    @Test
    void 이미_사용된_코드는_폐기할_수_없다() {
      given(participationCodeRepository.findById(7L)).willReturn(Optional.of(code(SLOT_ID)));
      given(participationCodeRepository.revokeIfActive(eq(7L), any())).willReturn(false);

      assertThatThrownBy(() -> participationCodeDomainService.revoke(7L, NOW))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_CODE_REVOKE_NOT_ALLOWED);
    }
  }
}
