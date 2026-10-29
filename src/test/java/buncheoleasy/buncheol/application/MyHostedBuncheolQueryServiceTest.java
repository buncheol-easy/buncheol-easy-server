package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolHostCancellability;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.BuncheolConfirmedParticipationCount;
import buncheoleasy.buncheol.domain.ConfirmCheck;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.BuncheolUnconfirmedParticipationCount;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyHostedBuncheolQueryService 단위 테스트")
class MyHostedBuncheolQueryServiceTest {

  @InjectMocks private MyHostedBuncheolQueryService myHostedBuncheolQueryService;

  @Mock private BuncheolRepository buncheolRepository;
  @Mock private BuncheolMemberRepository buncheolMemberRepository;
  @Mock private ParticipationRepository participationRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private BuncheolImageRepository buncheolImageRepository;
  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private static final Long HOST_ID = 1L;
  private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");

  @Nested
  @DisplayName("내 개최 분철 목록 조회 테스트")
  class GetMyHostedBuncheolsTest {

    @Test
    void 개최한_분철이_없으면_빈_리스트를_반환한다() {
      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID)).willReturn(List.of());

      List<MyHostedBuncheolResponse> result =
          myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID);

      assertThat(result).isEmpty();
    }

    @Test
    void 분철_정보_그룹명_슬롯수_활성참여수를_조합해_반환한다() {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Instant createdAt = Instant.parse("2026-05-01T09:00:00Z");
      Buncheol buncheol =
          buncheol(10L, 100L, "뉴진스 1집 분철", BuncheolStatus.RECRUITING, deadline, createdAt);

      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID))
          .willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
          .willReturn(
              List.of(
                  buncheolMember(101L, 10L, 1001L),
                  buncheolMember(102L, 10L, 1002L),
                  buncheolMember(103L, 10L, 1003L)));
      given(participationRepository.countActiveByBuncheolIds(List.of(10L)))
          .willReturn(List.of(new BuncheolActiveParticipationCount(10L, 7L)));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L)))
          .willReturn(List.of(BuncheolImage.create(10L, "https://cdn.example.com/10-thumb.jpg", false)));

      List<MyHostedBuncheolResponse> result =
          myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID);

      assertThat(result).hasSize(1);
      MyHostedBuncheolResponse response = result.get(0);
      assertThat(response.buncheolId()).isEqualTo(10L);
      assertThat(response.title()).isEqualTo("뉴진스 1집 분철");
      assertThat(response.groupName()).isEqualTo("뉴진스");
      assertThat(response.status()).isEqualTo(BuncheolStatus.RECRUITING);
      assertThat(response.deadline()).isEqualTo(deadline);
      assertThat(response.memberSlotCount()).isEqualTo(3);
      assertThat(response.activeParticipationCount()).isEqualTo(7L);
      assertThat(response.createdAt()).isEqualTo(createdAt);
      assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.example.com/10-thumb.jpg");
    }

    @Test
    void 활성_참여가_없는_분철은_count_0으로_반환한다() {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Instant createdAt = Instant.parse("2026-05-01T09:00:00Z");
      Buncheol buncheol =
          buncheol(10L, 100L, "분철 A", BuncheolStatus.RECRUITING, deadline, createdAt);

      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID))
          .willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
          .willReturn(List.of(buncheolMember(101L, 10L, 1001L)));
      given(participationRepository.countActiveByBuncheolIds(List.of(10L))).willReturn(List.of());
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());

      List<MyHostedBuncheolResponse> result =
          myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).activeParticipationCount()).isZero();
      // 이미지가 없는 분철은 썸네일 없이 내려간다.
      assertThat(result.get(0).thumbnailUrl()).isNull();
    }

    @Test
    void 여러_분철은_최신_개최순으로_매핑된다() {
      Instant now = Instant.parse("2026-05-01T09:00:00Z");
      Buncheol newer =
          buncheol(
              20L, 200L, "분철 NEW", BuncheolStatus.RECRUITING, now.plus(7, ChronoUnit.DAYS), now);
      Buncheol older =
          buncheol(
              10L,
              100L,
              "분철 OLD",
              BuncheolStatus.CONFIRMED,
              now.minus(1, ChronoUnit.DAYS),
              now.minus(30, ChronoUnit.DAYS));

      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID))
          .willReturn(List.of(newer, older));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(20L, 10L)))
          .willReturn(
              List.of(
                  buncheolMember(201L, 20L, 2001L),
                  buncheolMember(202L, 20L, 2002L),
                  buncheolMember(101L, 10L, 1001L)));
      given(participationRepository.countActiveByBuncheolIds(List.of(20L, 10L)))
          .willReturn(
              List.of(
                  new BuncheolActiveParticipationCount(20L, 3L),
                  new BuncheolActiveParticipationCount(10L, 1L)));
      given(groupRepository.findAllByIds(List.of(200L, 100L)))
          .willReturn(List.of(group(200L, "에스파"), group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(20L, 10L)))
          .willReturn(
              List.of(
                  BuncheolImage.create(20L, "https://cdn.example.com/20-thumb.jpg", false),
                  BuncheolImage.create(10L, "https://cdn.example.com/10-thumb.jpg", false)));

      List<MyHostedBuncheolResponse> result =
          myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).buncheolId()).isEqualTo(20L);
      assertThat(result.get(0).groupName()).isEqualTo("에스파");
      assertThat(result.get(0).memberSlotCount()).isEqualTo(2);
      assertThat(result.get(0).activeParticipationCount()).isEqualTo(3L);
      assertThat(result.get(1).buncheolId()).isEqualTo(10L);
      assertThat(result.get(1).groupName()).isEqualTo("뉴진스");
      assertThat(result.get(1).memberSlotCount()).isEqualTo(1);
      assertThat(result.get(1).activeParticipationCount()).isEqualTo(1L);
      assertThat(result.get(0).thumbnailUrl()).isEqualTo("https://cdn.example.com/20-thumb.jpg");
      assertThat(result.get(1).thumbnailUrl()).isEqualTo("https://cdn.example.com/10-thumb.jpg");
    }
  }

  @Nested
  @DisplayName("열린 자리 수 집계 테스트")
  class OpenSlotCountTest {

    // 「멤버 N명」은 removeMember 가 마지막 자리 판정에 쓰는 수와 같아야 한다 — 닫힌 자리만 빼고 코드 자리는 센다.
    @Test
    void 닫힌_자리는_memberSlotCount에서_빠지고_코드_자리는_센다() {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Instant createdAt = Instant.parse("2026-05-01T09:00:00Z");
      Buncheol withClosed =
          buncheol(10L, 100L, "닫힌 자리가 있는 분철", BuncheolStatus.RECRUITING, deadline, createdAt);
      Buncheol withoutClosed =
          buncheol(20L, 100L, "닫힌 자리가 없는 분철", BuncheolStatus.RECRUITING, deadline, createdAt);

      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID))
          .willReturn(List.of(withClosed, withoutClosed));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L, 20L)))
          .willReturn(
              List.of(
                  buncheolMember(101L, 10L, 1001L, BuncheolMemberAccessType.OPEN),
                  buncheolMember(102L, 10L, 1002L, BuncheolMemberAccessType.CLOSED),
                  buncheolMember(103L, 10L, 1003L, BuncheolMemberAccessType.CODE_ONLY),
                  buncheolMember(201L, 20L, 1001L, BuncheolMemberAccessType.OPEN),
                  buncheolMember(202L, 20L, 1002L, BuncheolMemberAccessType.OPEN)));
      given(participationRepository.countActiveByBuncheolIds(List.of(10L, 20L))).willReturn(List.of());
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L, 20L)))
          .willReturn(List.of());

      List<MyHostedBuncheolResponse> result =
          myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID);

      assertThat(result)
          .extracting(
              MyHostedBuncheolResponse::buncheolId, MyHostedBuncheolResponse::memberSlotCount)
          .containsExactly(tuple(10L, 2), tuple(20L, 2));
    }
  }

  @Nested
  @DisplayName("취소 가능 여부 노출 테스트 (docs/56 S-2)")
  class CancellabilityTest {

    // 계좌가 공개된 뒤라 입금 확인 건수와 무관하게 운영진 경유다 — 화면은 삭제 대신 「운영진에게 알려 주세요」를 띄운다.
    @Test
    void 입금_수집중_분철은_운영진_경유로_내려간다() {
      List<MyHostedBuncheolResponse> result = 응답(BuncheolStatus.PAYMENT_COLLECTING);

      assertThat(result.get(0).cancellability())
          .isEqualTo(BuncheolHostCancellability.NEEDS_STAFF);
    }

    @Test
    void 모집중_분철은_취소_가능으로_내려간다() {
      List<MyHostedBuncheolResponse> result = 응답(BuncheolStatus.RECRUITING);

      assertThat(result.get(0).cancellability())
          .isEqualTo(BuncheolHostCancellability.CANCELLABLE);
    }

    @Test
    void 진행확정된_분철은_상태_사유로_취소_불가로_내려간다() {
      List<MyHostedBuncheolResponse> result = 응답(BuncheolStatus.CONFIRMED);

      assertThat(result.get(0).cancellability())
          .isEqualTo(BuncheolHostCancellability.BLOCKED_BY_STATUS);
    }

    private List<MyHostedBuncheolResponse> 응답(final BuncheolStatus status) {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Instant createdAt = Instant.parse("2026-05-01T09:00:00Z");
      Buncheol buncheol = buncheol(10L, 100L, "분철", status, deadline, createdAt);

      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID))
          .willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
          .willReturn(List.of(buncheolMember(101L, 10L, 1001L)));
      given(participationRepository.countActiveByBuncheolIds(List.of(10L))).willReturn(List.of());
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L))).willReturn(List.of());

      List<MyHostedBuncheolResponse> result =
          myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID);
      assertThat(result).hasSize(1);
      return result;
    }
  }

  @Nested
  @DisplayName("종료 여부 노출 테스트")
  class EndedTest {

    private static final Instant DEADLINE = Instant.parse("2026-06-01T12:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-05-01T09:00:00Z");

    @Test
    void 진행확정_분철만_한_번에_물어_그_결과로_ended를_채운다() {
      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID))
          .willReturn(
              List.of(
                  분철(10L, BuncheolStatus.CONFIRMED),
                  분철(20L, BuncheolStatus.CONFIRMED),
                  분철(30L, BuncheolStatus.RECRUITING),
                  분철(40L, BuncheolStatus.PAYMENT_COLLECTING),
                  분철(50L, BuncheolStatus.CANCELLED)));
      // 판정 대상은 진행확정 두 건뿐이다 — 다른 id 가 섞여 넘어오면 strict stub 이 인자 불일치로 실패한다.
      given(buncheolRepository.findEndedIds(List.of(10L, 20L))).willReturn(Set.of(10L));

      List<MyHostedBuncheolResponse> result =
          myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID);

      // 취소(CANCELLED)도 false 다 — 「종료」 탭이 취소를 담을지는 화면이 status 와 묶어 정한다.
      assertThat(result)
          .extracting(MyHostedBuncheolResponse::buncheolId, MyHostedBuncheolResponse::ended)
          .containsExactly(
              tuple(10L, true),
              tuple(20L, false),
              tuple(30L, false),
              tuple(40L, false),
              tuple(50L, false));
      // 분철마다 묻지 않고 한 번에 묻는다 (N+1 금지).
      verify(buncheolRepository).findEndedIds(anyList());
    }

    // 쿼리를 건너뛰는 것은 어댑터 몫이다 (countConfirmedByBuncheolIds 와 같은 분담) — 서비스는 빈 목록을 넘기면 된다.
    @Test
    void 진행확정_분철이_없으면_빈_대상을_넘기고_전부_false로_내린다() {
      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID))
          .willReturn(List.of(분철(30L, BuncheolStatus.RECRUITING), 분철(50L, BuncheolStatus.CANCELLED)));

      List<MyHostedBuncheolResponse> result =
          myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID);

      assertThat(result).extracting(MyHostedBuncheolResponse::ended).containsOnly(false);
      verify(buncheolRepository).findEndedIds(List.of());
    }

    @Test
    void 추가_모집_스위치_값을_그대로_싣는다() {
      Buncheol open = 분철(10L, BuncheolStatus.CONFIRMED);
      setField(open, "extraRecruitOpen", true);
      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID))
          .willReturn(List.of(open, 분철(20L, BuncheolStatus.CONFIRMED)));

      List<MyHostedBuncheolResponse> result =
          myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID);

      assertThat(result)
          .extracting(
              MyHostedBuncheolResponse::buncheolId, MyHostedBuncheolResponse::extraRecruitOpen)
          .containsExactly(tuple(10L, true), tuple(20L, false));
    }

    private Buncheol 분철(final Long id, final BuncheolStatus status) {
      return buncheol(id, 100L, "분철 " + id, status, DEADLINE, CREATED_AT);
    }
  }

  @Nested
  @DisplayName("진행 확정 판정 노출 테스트")
  class ConfirmCheckTest {

    // 관리 화면·진행 확정 게이트와 같은 판정이다. 확인·미확인 수는 입금 수집중 분철만 골라 한 번에 센다.
    private ConfirmCheck 판정(
        final BuncheolStatus status,
        final Instant paymentDueAt,
        final long active,
        final long confirmed,
        final long unconfirmed) {
      Buncheol buncheol =
          buncheol(10L, 100L, "분철", status, NOW.plusSeconds(3600), NOW.minusSeconds(86_400));
      setField(buncheol, "paymentDueAt", paymentDueAt);
      given(buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(HOST_ID))
          .willReturn(List.of(buncheol));
      given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
          .willReturn(List.of(buncheolMember(101L, 10L, 1001L)));
      given(participationRepository.countActiveByBuncheolIds(List.of(10L)))
          .willReturn(List.of(new BuncheolActiveParticipationCount(10L, active)));
      List<Long> collectingIds =
          status == BuncheolStatus.PAYMENT_COLLECTING ? List.of(10L) : List.of();
      given(participationRepository.countConfirmedByBuncheolIds(collectingIds))
          .willReturn(
              collectingIds.isEmpty() || confirmed == 0
                  ? List.of()
                  : List.of(new BuncheolConfirmedParticipationCount(10L, confirmed)));
      given(participationRepository.countUnconfirmedByBuncheolIds(collectingIds))
          .willReturn(
              collectingIds.isEmpty() || unconfirmed == 0
                  ? List.of()
                  : List.of(new BuncheolUnconfirmedParticipationCount(10L, unconfirmed)));
      given(groupRepository.findAllByIds(List.of(100L))).willReturn(List.of(group(100L, "뉴진스")));
      given(buncheolImageRepository.findThumbnailsByBuncheolIds(List.of(10L)))
          .willReturn(List.of());
      return myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID).get(0).confirmCheck();
    }

    @Test
    void 모두_입금_확인됐으면_ALL_PAID() {
      assertThat(판정(BuncheolStatus.PAYMENT_COLLECTING, NOW.plusSeconds(3600), 2, 2, 0))
          .isEqualTo(ConfirmCheck.ALL_PAID);
    }

    // 입금 수집중에 신청(APPLIED)이 남은 옛 데이터: 확인 1 + 신청 1. 진행 확정 가드는 미확인을 입금 대기·보냈어요로만 세어
    // ALL_PAID 로 보므로 목록도 같아야 한다. 「살아 있는 참여 − 확인」으로 세면 미확인 1 이 되어 BEFORE_DUE 로 갈린다.
    @Test
    void 신청이_남은_입금_수집중_분철도_진행_확정_가드와_같은_판정을_낸다() {
      assertThat(판정(BuncheolStatus.PAYMENT_COLLECTING, NOW.plusSeconds(3600), 2, 1, 0))
          .isEqualTo(ConfirmCheck.ALL_PAID);
    }

    @Test
    void 기한이_지났고_확인된_참여가_있으면_DUE_PASSED() {
      assertThat(판정(BuncheolStatus.PAYMENT_COLLECTING, NOW.minusSeconds(1), 3, 1, 2))
          .isEqualTo(ConfirmCheck.DUE_PASSED);
    }

    // 확인된 자리가 없으면 진행 확정 버튼이 꺼져 있어 「진행 확정해 주세요」 배지를 띄우지 않는다.
    @Test
    void 확인된_참여가_없으면_기한이_지나도_NO_CONFIRMED() {
      assertThat(판정(BuncheolStatus.PAYMENT_COLLECTING, NOW.minusSeconds(1), 2, 0, 2))
          .isEqualTo(ConfirmCheck.NO_CONFIRMED);
    }

    @Test
    void 입금_수집중이_아니면_null_이고_확인_수를_세지_않는다() {
      assertThat(판정(BuncheolStatus.RECRUITING, null, 1, 0, 0)).isNull();
    }
  }

  private Buncheol buncheol(
      Long id,
      Long groupId,
      String title,
      BuncheolStatus status,
      Instant deadline,
      Instant createdAt) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "groupId", groupId);
    setField(buncheol, "title", title);
    setField(buncheol, "status", status);
    setField(buncheol, "deadline", deadline);
    setField(buncheol, "createdAt", createdAt);
    return buncheol;
  }

  private BuncheolMember buncheolMember(Long id, Long buncheolId, Long memberId) {
    BuncheolMember member = newInstance(BuncheolMember.class);
    setField(member, "id", id);
    setField(member, "buncheolId", buncheolId);
    setField(member, "memberId", memberId);
    return member;
  }

  private BuncheolMember buncheolMember(
      Long id, Long buncheolId, Long memberId, BuncheolMemberAccessType accessType) {
    BuncheolMember member = buncheolMember(id, buncheolId, memberId);
    setField(member, "accessType", accessType);
    return member;
  }

  private Group group(Long id, String name) {
    Group group = newInstance(Group.class);
    setField(group, "id", id);
    setField(group, "name", name);
    return group;
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
