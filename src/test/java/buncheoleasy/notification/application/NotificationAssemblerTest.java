package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationAssembler 단위 테스트")
class NotificationAssemblerTest {

  @InjectMocks private NotificationAssembler assembler;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;
  @Mock private GroupDomainService groupDomainService;
  @Mock private UserDomainService userDomainService;
  @Mock private DeliveryDomainService deliveryDomainService;

  private static final Long BUNCHEOL_ID = 5L;
  private static final Long GROUP_ID = 77L;
  private static final Long PARTICIPANT_ID = 9L;

  @Nested
  @DisplayName("슬롯 묶음 조회(loadByParticipations)")
  class LoadByParticipations {

    @Test
    @DisplayName("공통 컨텍스트는 첫 참여 기준 1회만 조회하고, 멤버명은 배치 조회 결과 순서와 무관하게 참여 순서를 유지한다")
    void loadsCommonContextOnceAndKeepsSlotOrder() {
      Instant dueAt = Instant.parse("2026-07-06T03:30:00Z");
      RefundAccount refundAccount = RefundAccount.of("국민은행", "11012345678", "김참여");
      Participation first = mock(Participation.class);
      given(first.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(first.getParticipantId()).willReturn(PARTICIPANT_ID);
      given(first.getBuncheolMemberId()).willReturn(101L);
      given(first.getTotalAmount()).willReturn(23_000L);
      given(first.getDueAt()).willReturn(dueAt);
      given(first.getRefundAccount()).willReturn(refundAccount);
      Participation second = mock(Participation.class);
      given(second.getBuncheolMemberId()).willReturn(102L);
      given(second.getTotalAmount()).willReturn(20_000L);
      given(participationDomainService.getParticipation(1L)).willReturn(first);
      given(participationDomainService.getParticipation(2L)).willReturn(second);

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.getGroupId()).willReturn(GROUP_ID);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      User participant = mock(User.class);
      given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(participant);

      BuncheolMember firstSlot = buncheolMember(1001L);
      BuncheolMember secondSlot = buncheolMember(1002L);
      given(buncheolMemberDomainService.getBuncheolMember(101L, BUNCHEOL_ID))
          .willReturn(firstSlot);
      given(buncheolMemberDomainService.getBuncheolMember(102L, BUNCHEOL_ID))
          .willReturn(secondSlot);
      // 그룹 멤버 배치 조회가 요청 순서와 반대로 돌려줘도 참여(슬롯) 순서를 유지해야 한다.
      List<GroupMember> reversedMembers =
          List.of(groupMember(1002L, "해원"), groupMember(1001L, "설윤"));
      given(groupDomainService.getGroupMembersByIdsInGroup(GROUP_ID, List.of(1001L, 1002L)))
          .willReturn(reversedMembers);

      ParticipationBundleView view = assembler.loadByParticipations(List.of(1L, 2L));

      assertThat(view.buncheol()).isEqualTo(buncheol);
      assertThat(view.participant()).isEqualTo(participant);
      assertThat(view.memberNames()).containsExactly("설윤", "해원");
      assertThat(view.totalAmount()).isEqualTo(43_000L);
      assertThat(view.dueAt()).isEqualTo(dueAt);
      assertThat(view.refundAccount()).isEqualTo(refundAccount);

      // 분철·참여자는 묶음당 1회, 그룹 멤버는 전체 memberIds 로 1회 배치 조회.
      then(buncheolDomainService).should().getBuncheol(BUNCHEOL_ID);
      then(userDomainService).should().getUser(PARTICIPANT_ID);
      then(groupDomainService).should().getGroupMembersByIdsInGroup(GROUP_ID, List.of(1001L, 1002L));
    }
  }

  private BuncheolMember buncheolMember(final Long memberId) {
    BuncheolMember buncheolMember = mock(BuncheolMember.class);
    given(buncheolMember.getMemberId()).willReturn(memberId);
    return buncheolMember;
  }

  private GroupMember groupMember(final Long id, final String name) {
    GroupMember groupMember = mock(GroupMember.class);
    given(groupMember.getId()).willReturn(id);
    given(groupMember.getName()).willReturn(name);
    return groupMember;
  }
}
