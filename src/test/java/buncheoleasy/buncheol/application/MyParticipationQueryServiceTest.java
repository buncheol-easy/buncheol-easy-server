package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.response.MyParticipationResponse;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyParticipationQueryService 단위 테스트")
class MyParticipationQueryServiceTest {

  @InjectMocks private MyParticipationQueryService myParticipationQueryService;

  @Mock private ParticipationRepository participationRepository;
  @Mock private BuncheolRepository buncheolRepository;
  @Mock private BuncheolMemberRepository buncheolMemberRepository;
  @Mock private GroupMemberRepository groupMemberRepository;

  private static final Long PARTICIPANT_ID = 100L;

  @Nested
  @DisplayName("내 참여 목록 조회 테스트")
  class GetMyParticipationsTest {

    @Test
    void 참여_내역이_없으면_빈_리스트를_반환한다() {
      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of());

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).isEmpty();
    }

    @Test
    void 참여_정보_분철_정보_멤버_이름_슬롯_수를_조합해_반환한다() {
      // 분철 1: id=10, title="뉴진스 1집 분철", deadline=+7일, status=RECRUITING, 슬롯 5개
      Buncheol buncheol =
          buncheol(
              10L, "뉴진스 1집 분철", Instant.now().plus(7, ChronoUnit.DAYS), BuncheolStatus.RECRUITING);
      List<BuncheolMember> slots =
          List.of(
              buncheolMember(101L, 10L, 1001L),
              buncheolMember(102L, 10L, 1002L),
              buncheolMember(103L, 10L, 1003L),
              buncheolMember(104L, 10L, 1004L),
              buncheolMember(105L, 10L, 1005L));
      // 내가 참여한 슬롯: 102 (멤버 group_members.id=1002 → 이름 "민지")
      Participation participation =
          participation(500L, 10L, 102L, 50_000L, ParticipationStatus.ACTIVE_BID, null, null);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(participation));
      given(buncheolRepository.findAllByIds(List.of(10L))).willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L))).willReturn(slots);
      // 참여한 슬롯(102) 의 멤버(1002) 만 조회된다.
      given(groupMemberRepository.findAllByIds(List.of(1002L)))
          .willReturn(List.of(groupMember(1002L, "민지")));

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).hasSize(1);
      MyParticipationResponse response = result.get(0);
      assertThat(response.participationId()).isEqualTo(500L);
      assertThat(response.buncheolId()).isEqualTo(10L);
      assertThat(response.buncheolTitle()).isEqualTo("뉴진스 1집 분철");
      assertThat(response.buncheolMemberCount()).isEqualTo(5);
      assertThat(response.memberName()).isEqualTo("민지");
      assertThat(response.bidAmount()).isEqualTo(50_000L);
      assertThat(response.participationStatus()).isEqualTo(ParticipationStatus.ACTIVE_BID);
      assertThat(response.buncheolStatus()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(response.buncheolDeadline()).isEqualTo(buncheol.getDeadline());
      assertThat(response.paymentDueAt()).isNull();
      assertThat(response.closedRank()).isNull();
    }

    @Test
    void 개최자_취소로_자동_CANCELLED_된_참여도_응답에_포함된다() {
      // 호스트가 분철을 취소하면 ACTIVE 참여는 BuncheolService 흐름에서 자동 CANCELLED 로 전이된다.
      // 따라서 분철은 CANCELLED, 참여도 CANCELLED 상태이며, 사용자에겐 "취소된 참여 이력" 으로 그대로 보여야 한다.
      // (KAN-88 이전 동작: CANCELLED 분철의 참여를 응답에서 제외 → 사용자는 자신의 참여 이력을 잃었음.
      //  이제는 자동 cancel cascade 가 도메인을 일관되게 만들어 줘서 응답에 그대로 노출 가능.)
      Buncheol cancelled =
          buncheol(
              20L, "개최자가 취소한 분철", Instant.now().plus(7, ChronoUnit.DAYS), BuncheolStatus.CANCELLED);
      Participation autoCancelled =
          participation(600L, 20L, 201L, 30_000L, ParticipationStatus.CANCELLED, null, null);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(autoCancelled));
      given(buncheolRepository.findAllByIds(List.of(20L))).willReturn(List.of(cancelled));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(20L)))
          .willReturn(List.of(buncheolMember(201L, 20L, 2001L)));
      given(groupMemberRepository.findAllByIds(List.of(2001L)))
          .willReturn(List.of(groupMember(2001L, "지수")));

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).buncheolId()).isEqualTo(20L);
      assertThat(result.get(0).participationStatus())
          .isEqualTo(ParticipationStatus.CANCELLED);
      assertThat(result.get(0).buncheolStatus()).isEqualTo(BuncheolStatus.CANCELLED);
    }

    @Test
    void 여러_분철에_참여한_경우_분철별로_필드를_매핑한다() {
      Buncheol buncheolA =
          buncheol(10L, "분철 A", Instant.now().plus(3, ChronoUnit.DAYS), BuncheolStatus.CLOSED);
      Buncheol buncheolB =
          buncheol(20L, "분철 B", Instant.now().plus(5, ChronoUnit.DAYS), BuncheolStatus.RECRUITING);

      // A: 슬롯 2개 (참여한 슬롯 = 201, 멤버 이름 "지수")
      // B: 슬롯 4개 (참여한 슬롯 = 301, 멤버 이름 "제니")
      List<BuncheolMember> slots =
          List.of(
              buncheolMember(201L, 10L, 2001L),
              buncheolMember(202L, 10L, 2002L),
              buncheolMember(301L, 20L, 3001L),
              buncheolMember(302L, 20L, 3002L),
              buncheolMember(303L, 20L, 3003L),
              buncheolMember(304L, 20L, 3004L));

      Instant dueAt = Instant.now().plus(1, ChronoUnit.DAYS);
      Participation pA =
          participation(500L, 10L, 201L, 80_000L, ParticipationStatus.AWAITING_PAYMENT, dueAt, 1);
      Participation pB =
          participation(600L, 20L, 301L, 30_000L, ParticipationStatus.ACTIVE_BID, null, null);

      given(participationRepository.findAllByParticipantIdOrderByCreatedAtDesc(PARTICIPANT_ID))
          .willReturn(List.of(pA, pB));
      given(buncheolRepository.findAllByIds(List.of(10L, 20L)))
          .willReturn(List.of(buncheolA, buncheolB));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L, 20L))).willReturn(slots);
      // 참여한 슬롯(201, 301) 의 멤버(2001, 3001) 만 조회된다.
      given(groupMemberRepository.findAllByIds(List.of(2001L, 3001L)))
          .willReturn(List.of(groupMember(2001L, "지수"), groupMember(3001L, "제니")));

      List<MyParticipationResponse> result =
          myParticipationQueryService.getMyParticipations(PARTICIPANT_ID);

      assertThat(result).hasSize(2);

      MyParticipationResponse first = result.get(0);
      assertThat(first.buncheolTitle()).isEqualTo("분철 A");
      assertThat(first.buncheolMemberCount()).isEqualTo(2);
      assertThat(first.memberName()).isEqualTo("지수");
      assertThat(first.bidAmount()).isEqualTo(80_000L);
      assertThat(first.participationStatus()).isEqualTo(ParticipationStatus.AWAITING_PAYMENT);
      assertThat(first.buncheolStatus()).isEqualTo(BuncheolStatus.CLOSED);
      assertThat(first.paymentDueAt()).isEqualTo(dueAt);
      assertThat(first.closedRank()).isEqualTo(1);

      MyParticipationResponse second = result.get(1);
      assertThat(second.buncheolTitle()).isEqualTo("분철 B");
      assertThat(second.buncheolMemberCount()).isEqualTo(4);
      assertThat(second.memberName()).isEqualTo("제니");
      assertThat(second.bidAmount()).isEqualTo(30_000L);
      assertThat(second.participationStatus()).isEqualTo(ParticipationStatus.ACTIVE_BID);
      assertThat(second.buncheolStatus()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(second.paymentDueAt()).isNull();
      assertThat(second.closedRank()).isNull();
    }
  }

  private Buncheol buncheol(Long id, String title, Instant deadline, BuncheolStatus status) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "title", title);
    setField(buncheol, "deadline", deadline);
    setField(buncheol, "status", status);
    return buncheol;
  }

  private BuncheolMember buncheolMember(Long id, Long buncheolId, Long memberId) {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", id);
    setField(member, "buncheolId", buncheolId);
    setField(member, "memberId", memberId);
    return member;
  }

  private GroupMember groupMember(Long id, String name) {
    return new GroupMember(id, 1L, name, null);
  }

  private Participation participation(
      Long id,
      Long buncheolId,
      Long buncheolMemberId,
      Long bidAmount,
      ParticipationStatus status,
      Instant dueAt,
      Integer closedRank) {
    Participation participation =
        Participation.create(buncheolId, buncheolMemberId, PARTICIPANT_ID, 1L, bidAmount);
    setField(participation, "id", id);
    setField(participation, "status", status);
    setField(participation, "dueAt", dueAt);
    setField(participation, "closedRank", closedRank);
    return participation;
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      Field field = findField(target.getClass(), fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
