package buncheoleasy.admin.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.admin.dto.request.AdminParticipationCodeIssueRequest;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.code.ParticipationCodeDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ReflectionUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminParticipationCodeService 단위 테스트")
class AdminParticipationCodeServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-26T06:00:00Z");
  private static final Long BUNCHEOL_ID = 24L;
  private static final Long SLOT_ID = 76L;

  @InjectMocks private AdminParticipationCodeService adminParticipationCodeService;

  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;
  @Mock private ParticipationCodeDomainService participationCodeDomainService;
  @Mock private ParticipationRepository participationRepository;
  @Mock private GroupMemberRepository groupMemberRepository;

  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private static void setField(final Object target, final String name, final Object value) {
    Field field = ReflectionUtils.findField(target.getClass(), name);
    ReflectionUtils.makeAccessible(field);
    ReflectionUtils.setField(field, target, value);
  }

  private static <T> T newInstance(final Class<T> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private BuncheolMember codeSlot() {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", SLOT_ID);
    setField(member, "buncheolId", BUNCHEOL_ID);
    setField(member, "memberId", 1001L);
    setField(member, "price", 0L);
    setField(member, "accessType", BuncheolMemberAccessType.CODE_ONLY);
    return member;
  }

  // 발급해도 참여 시점에 BCH-070 으로 막히는 헛 코드를 운영자가 차순위에게 보내는 것을 끊는다.
  @Test
  void 이미_참여가_확정된_슬롯에는_코드를_발급하지_않는다() {
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(newInstance(Buncheol.class));
    given(buncheolMemberDomainService.getBuncheolMember(SLOT_ID, BUNCHEOL_ID))
        .willReturn(codeSlot());
    given(participationRepository.existsActiveByBuncheolMemberId(SLOT_ID)).willReturn(true);

    assertThatThrownBy(
            () ->
                adminParticipationCodeService.issue(
                    BUNCHEOL_ID,
                    new AdminParticipationCodeIssueRequest(SLOT_ID, "@next", 48, false)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARTICIPATION_CODE_MEMBER_TAKEN);

    then(participationCodeDomainService).should(never()).issue(any(), any(), any(), any());
    then(participationCodeDomainService).should(never()).reissue(any(), any(), any(), any());
  }

  // 유료 슬롯을 코드 참여로 바꾸면 화면은 "0원" 을 안내하는데 서버는 유상 참여로 처리해 참여가 실패한다.
  @Test
  void 유료_슬롯은_코드_참여로_전환할_수_없다() {
    Buncheol buncheol = newInstance(Buncheol.class);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
    BuncheolMember paidSlot = codeSlot();
    setField(paidSlot, "price", 20_700L);
    setField(paidSlot, "accessType", BuncheolMemberAccessType.OPEN);
    given(buncheolMemberDomainService.getBuncheolMember(SLOT_ID, BUNCHEOL_ID))
        .willReturn(paidSlot);

    assertThatThrownBy(
            () ->
                adminParticipationCodeService.changeBuncheolMemberAccessType(
                    BUNCHEOL_ID, SLOT_ID, BuncheolMemberAccessType.CODE_ONLY))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARTICIPATION_CODE_MEMBER_NOT_FREE);

    then(buncheolMemberDomainService).should(never()).changeAccessType(any(), any(), any());
  }

  // CLOSED 는 C2C 개최자 「빼기」 전용 — 운영진이 아무 자리나 닫을 수 있으면 안 된다.
  @Test
  void 운영진은_CLOSED_로_바꿀_수_없다() {
    assertThatThrownBy(
            () ->
                adminParticipationCodeService.changeBuncheolMemberAccessType(
                    BUNCHEOL_ID, SLOT_ID, BuncheolMemberAccessType.CLOSED))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

    then(buncheolMemberDomainService).should(never()).changeAccessType(any(), any(), any());
  }

  // 🔴 C2C 자리를 OPEN 으로 바꾸면 개최자가 닫은 자리가 말없이 다시 열린다 — 열고 닫기는 개최자 경로만 쥔다.
  @Test
  void C2C_분철_자리는_OPEN_으로도_바꿀_수_없다() {
    Buncheol c2c = newInstance(Buncheol.class);
    setField(c2c, "flowType", FlowType.C2C);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(c2c);

    assertThatThrownBy(
            () ->
                adminParticipationCodeService.changeBuncheolMemberAccessType(
                    BUNCHEOL_ID, SLOT_ID, BuncheolMemberAccessType.OPEN))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_FLOW_NOT_SUPPORTED);

    then(buncheolMemberDomainService).should(never()).changeAccessType(any(), any(), any());
    then(participationCodeDomainService).should(never()).revokeOutstandingByMember(any(), any());
  }

  // 기존 C2C 코드 참여 거부(BCH-110)는 그대로 — 새 가드보다 먼저 걸린다.
  @Test
  void C2C_분철_코드_참여_전환은_기존_코드로_거부한다() {
    Buncheol c2c = newInstance(Buncheol.class);
    setField(c2c, "flowType", FlowType.C2C);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(c2c);

    assertThatThrownBy(
            () ->
                adminParticipationCodeService.changeBuncheolMemberAccessType(
                    BUNCHEOL_ID, SLOT_ID, BuncheolMemberAccessType.CODE_ONLY))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_C2C_CODE_MEMBER_NOT_ALLOWED);
  }

  // 회귀 — LEGACY 의 OPEN ↔ CODE_ONLY 전환은 그대로 된다.
  @Test
  void LEGACY_분철은_선착순으로_되돌리고_남은_코드를_폐기한다() {
    Buncheol legacy = newInstance(Buncheol.class);
    setField(legacy, "flowType", FlowType.LEGACY);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(legacy);

    adminParticipationCodeService.changeBuncheolMemberAccessType(
        BUNCHEOL_ID, SLOT_ID, BuncheolMemberAccessType.OPEN);

    then(buncheolMemberDomainService)
        .should()
        .changeAccessType(SLOT_ID, BUNCHEOL_ID, BuncheolMemberAccessType.OPEN);
    then(participationCodeDomainService).should().revokeOutstandingByMember(SLOT_ID, NOW);
  }

  @Test
  void LEGACY_분철의_0원_자리는_코드_참여로_바꾼다() {
    Buncheol legacy = newInstance(Buncheol.class);
    setField(legacy, "flowType", FlowType.LEGACY);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(legacy);
    BuncheolMember freeSlot = codeSlot();
    setField(freeSlot, "accessType", BuncheolMemberAccessType.OPEN);
    given(buncheolMemberDomainService.getBuncheolMember(SLOT_ID, BUNCHEOL_ID))
        .willReturn(freeSlot);

    adminParticipationCodeService.changeBuncheolMemberAccessType(
        BUNCHEOL_ID, SLOT_ID, BuncheolMemberAccessType.CODE_ONLY);

    then(buncheolMemberDomainService)
        .should()
        .changeAccessType(SLOT_ID, BUNCHEOL_ID, BuncheolMemberAccessType.CODE_ONLY);
  }

  @Test
  void 다른_슬롯의_참여는_발급을_막지_않는다() {
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(newInstance(Buncheol.class));
    given(buncheolMemberDomainService.getBuncheolMember(SLOT_ID, BUNCHEOL_ID))
        .willReturn(codeSlot());
    given(participationRepository.existsActiveByBuncheolMemberId(SLOT_ID)).willReturn(false);
    given(participationCodeDomainService.issue(any(), any(), any(), any()))
        .willThrow(new BusinessException(ErrorCode.PARTICIPATION_CODE_INVALID));

    assertThatThrownBy(
            () ->
                adminParticipationCodeService.issue(
                    BUNCHEOL_ID,
                    new AdminParticipationCodeIssueRequest(SLOT_ID, "@next", 48, false)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARTICIPATION_CODE_INVALID);
  }
}
