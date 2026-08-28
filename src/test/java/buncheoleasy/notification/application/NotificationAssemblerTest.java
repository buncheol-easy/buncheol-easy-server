package buncheoleasy.notification.application;

import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
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
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;
  @Mock private GroupDomainService groupDomainService;
  @Mock private UserDomainService userDomainService;
  @Mock private DeliveryDomainService deliveryDomainService;

  private static final Long BUNCHEOL_ID = 5L;
  private static final Long GROUP_ID = 77L;
  private static final Long PARTICIPANT_ID = 9L;
  private static final Long HOST_ID = 3L;

  @Nested
  @DisplayName("참여 단건 조회(loadByParticipation)")
  class LoadByParticipation {

    @Test
    @DisplayName("참여·분철·멤버명·참여자·개최자·입금액(배송비 포함 총액)을 조립한다")
    void loadsParticipationView() {
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participation.getParticipantId()).willReturn(PARTICIPANT_ID);
      given(participation.getBuncheolMemberId()).willReturn(101L);
      given(participation.getTotalAmount()).willReturn(23_000L);
      given(participationDomainService.getParticipation(1L)).willReturn(participation);

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.getGroupId()).willReturn(GROUP_ID);
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      // 헬퍼가 내부에서 스터빙하므로 바깥 given() 안에서 호출하면 UnfinishedStubbing 이 난다 — 먼저 만들어 둔다.
      BuncheolMember slot = buncheolMember(1001L);
      GroupMember groupMember = groupMember("설윤");
      given(buncheolMemberDomainService.getBuncheolMember(101L, BUNCHEOL_ID)).willReturn(slot);
      given(groupDomainService.getGroupMembersByIdsInGroup(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember));
      User participant = mock(User.class);
      User host = mock(User.class);
      given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(participant);
      given(userDomainService.getUser(HOST_ID)).willReturn(host);

      ParticipationView view = assembler.loadByParticipation(1L);

      assertThat(view.participation()).isEqualTo(participation);
      assertThat(view.buncheol()).isEqualTo(buncheol);
      assertThat(view.memberName()).isEqualTo("설윤");
      assertThat(view.participant()).isEqualTo(participant);
      assertThat(view.host()).isEqualTo(host);
      assertThat(view.paymentAmount()).isEqualTo(23_000L);
    }
  }

  private BuncheolMember buncheolMember(final Long memberId) {
    BuncheolMember buncheolMember = mock(BuncheolMember.class);
    given(buncheolMember.getMemberId()).willReturn(memberId);
    return buncheolMember;
  }

  // 단건 경로(resolveMemberName)는 이름만 읽으므로 getName 만 스터빙한다.
  private GroupMember groupMember(final String name) {
    GroupMember groupMember = mock(GroupMember.class);
    given(groupMember.getName()).willReturn(name);
    return groupMember;
  }
}
