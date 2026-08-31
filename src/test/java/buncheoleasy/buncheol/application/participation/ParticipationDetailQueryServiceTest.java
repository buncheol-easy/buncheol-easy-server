package buncheoleasy.buncheol.application.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.buncheol.application.payback.ShippingFeePaybackPolicy;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.buncheol.dto.response.ParticipationDetailResponse;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import buncheoleasy.user.domain.UserDomainService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationDetailQueryService 단위 테스트")
class ParticipationDetailQueryServiceTest {

  private static final Long PARTICIPANT_ID = 10L;
  private static final Long PARTICIPATION_ID = 500L;
  private static final Long BUNCHEOL_ID = 104L;
  private static final Long SLOT_ID = 101L;
  private static final Long MEMBER_ID = 1001L;
  private static final Long BUNDLE_ID = 9999L;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private BuncheolMemberDomainService buncheolMemberDomainService;
  @Mock private GroupMemberRepository groupMemberRepository;
  @Mock private UserDomainService userDomainService;
  @Mock private DeliveryRepository deliveryRepository;
  @Mock private ShippingFeePaybackPolicy shippingFeePaybackPolicy;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;

  private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC);


  // @InjectMocks 는 Clock 을 목으로 만들 수 없어(final 필드) 생성자로 직접 조립한다.
  private ParticipationDetailQueryService service() {
    return new ParticipationDetailQueryService(
        participationDomainService,
        buncheolDomainService,
        buncheolMemberDomainService,
        groupMemberRepository,
        userDomainService,
        deliveryRepository,
        shippingFeePaybackPolicy,
        participationBundleDomainService,
        clock);
  }

  // 🔴 이 값이 null 로 나가면 프론트가 묶음 단위 경로 대신 슬롯 경로로 조용히 폴백한다 — 화면만 보면 티가 안 난다.
  @Test
  @DisplayName("응답에 소속 묶음 id 가 실린다")
  void 응답에_소속_묶음_id가_실린다() {
    Participation participation =
        Participation.createApplied(BUNCHEOL_ID, SLOT_ID, PARTICIPANT_ID, 1L, 10_000L, 3_000L);
    setField(participation, "id", PARTICIPATION_ID);
    setField(participation, "bundleId", BUNDLE_ID);
    setField(participation, "status", ParticipationStatus.APPLIED);

    given(participationDomainService.getParticipation(PARTICIPATION_ID)).willReturn(participation);

    Buncheol buncheol = mock(Buncheol.class);
    given(buncheol.getId()).willReturn(BUNCHEOL_ID);
    lenient().when(buncheol.getTitle()).thenReturn("C2C 분철");
    lenient().when(buncheol.getFlowType()).thenReturn(FlowType.C2C);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);

    BuncheolMember slot = mock(BuncheolMember.class);
    given(slot.getMemberId()).willReturn(MEMBER_ID);
    given(buncheolMemberDomainService.getBuncheolMember(SLOT_ID, BUNCHEOL_ID)).willReturn(slot);
    GroupMember groupMember = mock(GroupMember.class);
    lenient().when(groupMember.getName()).thenReturn("설윤");
    given(groupMemberRepository.findAllByIds(List.of(MEMBER_ID))).willReturn(List.of(groupMember));

    given(deliveryRepository.findByParticipationId(PARTICIPATION_ID))
        .willReturn(Optional.empty());
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.empty());
    given(participationBundleDomainService.shippingFeeAttributionFor(participation))
        .willReturn(ShippingFeeAttribution.empty());

    ParticipationDetailResponse response =
        service().getDetail(PARTICIPANT_ID, PARTICIPATION_ID);

    assertThat(response.bundleId()).isEqualTo(BUNDLE_ID);
  }
}
