package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.participation.BuncheolActiveParticipationCount;
import buncheoleasy.buncheol.domain.participation.ParticipationRepository;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
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
@DisplayName("MyHostedBuncheolQueryService 단위 테스트")
class MyHostedBuncheolQueryServiceTest {

  @InjectMocks private MyHostedBuncheolQueryService myHostedBuncheolQueryService;

  @Mock private BuncheolRepository buncheolRepository;
  @Mock private BuncheolMemberRepository buncheolMemberRepository;
  @Mock private ParticipationRepository participationRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private BuncheolImageRepository buncheolImageRepository;

  private static final Long HOST_ID = 1L;

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
      given(buncheolImageRepository.findFirstByBuncheolIds(List.of(10L)))
          .willReturn(List.of(BuncheolImage.create(10L, "https://cdn.example.com/10-thumb.jpg")));

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
      given(buncheolImageRepository.findFirstByBuncheolIds(List.of(10L))).willReturn(List.of());

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
      given(buncheolImageRepository.findFirstByBuncheolIds(List.of(20L, 10L)))
          .willReturn(
              List.of(
                  BuncheolImage.create(20L, "https://cdn.example.com/20-thumb.jpg"),
                  BuncheolImage.create(10L, "https://cdn.example.com/10-thumb.jpg")));

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
