package buncheoleasy.admin.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.admin.dto.request.AdminParticipationCodeIssueRequest;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.code.ParticipationCodeDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.domain.participation.Participation;
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

  private Participation participationOn(final Long slotId) {
    Participation participation = newInstance(Participation.class);
    setField(participation, "id", 25L);
    setField(participation, "buncheolId", BUNCHEOL_ID);
    setField(participation, "buncheolMemberId", slotId);
    return participation;
  }

  // 발급해도 참여 시점에 BCH-070 으로 막히는 헛 코드를 운영자가 차순위에게 보내는 것을 끊는다.
  @Test
  void 이미_참여가_확정된_슬롯에는_코드를_발급하지_않는다() {
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(newInstance(Buncheol.class));
    given(buncheolMemberDomainService.getBuncheolMember(SLOT_ID, BUNCHEOL_ID))
        .willReturn(codeSlot());
    given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
        .willReturn(List.of(participationOn(SLOT_ID)));

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

  @Test
  void 다른_슬롯의_참여는_발급을_막지_않는다() {
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(newInstance(Buncheol.class));
    given(buncheolMemberDomainService.getBuncheolMember(SLOT_ID, BUNCHEOL_ID))
        .willReturn(codeSlot());
    given(participationRepository.findActiveByBuncheolId(BUNCHEOL_ID))
        .willReturn(List.of(participationOn(99L)));
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
