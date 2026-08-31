package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import java.util.List;
import java.util.Map;
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
      given(participation.getAmount()).willReturn(20_000L);
      given(participation.getShippingFee()).willReturn(3_000L);
      given(participationDomainService.getParticipation(1L)).willReturn(participation);
      // 묶음이 없는 경우(미연결) — 조립기는 읽어 둔 묶음을 그대로 넘긴다.
      given(participationBundleDomainService.shippingFeeAttributionOf(null))
          .willReturn(ShippingFeeAttribution.empty());

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

    // 🔴 알림톡이 화면과 다른 금액을 말하면 안 된다. 사용자는 "얼마 보내라"의 구속력이 큰 알림톡을 믿으므로,
    // 화면만 고치면 결함이 남는 게 아니라 두 숫자가 갈리는 상태가 새로 생긴다.
    @Test
    @DisplayName("배송비를 진 슬롯이 취소되면 이어받은 슬롯의 금액으로 알린다 — 저장값이 아니다")
    void usesAttributedAmountNotStoredTotal() {
      final Long bundleId = 141L;
      Participation participation = mock(Participation.class);
      given(participation.getBuncheolId()).willReturn(BUNCHEOL_ID);
      given(participation.getParticipantId()).willReturn(PARTICIPANT_ID);
      given(participation.getBuncheolMemberId()).willReturn(101L);
      given(participation.getId()).willReturn(233L);
      given(participation.getBundleId()).willReturn(bundleId);
      given(participation.getStatus()).willReturn(ParticipationStatus.AWAITING_PAYMENT);
      given(participation.getAmount()).willReturn(10_000L);
      // 저장된 배송비는 0 이다 — 배송비를 지고 있던 형제 슬롯이 취소됐기 때문이다. 이 값이 읽히지 않는 것이
      // 곧 이 테스트가 증명하려는 것이라 lenient 로 남겨 상황을 드러낸다.
      lenient().when(participation.getShippingFee()).thenReturn(0L);
      given(participationDomainService.getParticipation(1L)).willReturn(participation);

      ParticipationBundle bundle = mock(ParticipationBundle.class);
      // getId() 는 스텁하지 않는다 — 판정이 엔티티 게터가 아니라 맵 키로 carrier 를 찾으므로 필요 없고,
      // 그게 "목의 게터가 비어 기능이 조용히 꺼지는" 구멍을 구조적으로 없앤 결과다.
      given(bundle.getShippingFee()).willReturn(3_000L);
      // 판정을 given() 바깥에서 먼저 만든다 — 안에서 만들면 목을 읽는 사이에 UnfinishedStubbing 이 난다.
      ShippingFeeAttribution attribution =
          ShippingFeeAttribution.ofAllSlots(List.of(participation), Map.of(bundleId, bundle));
      given(participationBundleDomainService.findByParticipation(participation))
          .willReturn(java.util.Optional.of(bundle));
      given(participationBundleDomainService.shippingFeeAttributionOf(bundle))
          .willReturn(attribution);

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(BUNCHEOL_ID);
      given(buncheol.getGroupId()).willReturn(GROUP_ID);
      given(buncheol.getHostId()).willReturn(HOST_ID);
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
      BuncheolMember slot = buncheolMember(1001L);
      GroupMember groupMember = groupMember("설윤");
      given(buncheolMemberDomainService.getBuncheolMember(101L, BUNCHEOL_ID)).willReturn(slot);
      given(groupDomainService.getGroupMembersByIdsInGroup(GROUP_ID, List.of(1001L)))
          .willReturn(List.of(groupMember));
      given(userDomainService.getUser(PARTICIPANT_ID)).willReturn(mock(User.class));
      given(userDomainService.getUser(HOST_ID)).willReturn(mock(User.class));

      ParticipationView view = assembler.loadByParticipation(1L);

      // 저장값이면 10,000 이다. 귀속 판정으로 배송비를 이어받아 13,000 이어야 한다.
      assertThat(view.paymentAmount()).isEqualTo(13_000L);
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
