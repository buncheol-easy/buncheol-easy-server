package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolMemberNameResolver 단위 테스트")
class BuncheolMemberNameResolverTest {

  @InjectMocks private BuncheolMemberNameResolver resolver;

  @Mock private BuncheolMemberRepository buncheolMemberRepository;
  @Mock private GroupMemberRepository groupMemberRepository;

  @Test
  void 빈_입력이면_빈_맵을_반환하고_저장소를_호출하지_않는다() {
    Map<Long, List<String>> result = resolver.findNamesByBuncheolIds(List.of());

    assertThat(result).isEmpty();
    verifyNoInteractions(buncheolMemberRepository, groupMemberRepository);
  }

  @Test
  void buncheolMember_가_비어있으면_GroupMember_조회를_생략한다() {
    given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L))).willReturn(List.of());

    Map<Long, List<String>> result = resolver.findNamesByBuncheolIds(List.of(10L));

    assertThat(result).isEmpty();
    verifyNoInteractions(groupMemberRepository);
  }

  @Test
  void 분철별_멤버_이름이_BuncheolMember_id_오름차순으로_정렬되어_반환된다() {
    // 분철 10: 슬롯 등록 순(802 → 801) 이라도 정렬 후엔 801 → 802 순.
    // 분철 20: 단일 슬롯 803.
    given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L, 20L)))
        .willReturn(
            List.of(
                buncheolMember(802L, 10L, 2002L),
                buncheolMember(801L, 10L, 2001L),
                buncheolMember(803L, 20L, 3001L)));
    given(groupMemberRepository.findAllByIds(List.of(2002L, 2001L, 3001L)))
        .willReturn(
            List.of(groupMember(2002L, "민지"), groupMember(2001L, "하니"), groupMember(3001L, "카리나")));

    Map<Long, List<String>> result = resolver.findNamesByBuncheolIds(List.of(10L, 20L));

    assertThat(result.get(10L)).containsExactly("하니", "민지");
    assertThat(result.get(20L)).containsExactly("카리나");
  }

  @Test
  void GroupMember_가_조회되지_않은_슬롯은_결과에서_생략된다() {
    given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
        .willReturn(List.of(buncheolMember(800L, 10L, 999L), buncheolMember(801L, 10L, 1000L)));
    // 999 는 조회 안 됨 (삭제/소실 케이스).
    given(groupMemberRepository.findAllByIds(List.of(999L, 1000L)))
        .willReturn(List.of(groupMember(1000L, "혜인")));

    Map<Long, List<String>> result = resolver.findNamesByBuncheolIds(List.of(10L));

    assertThat(result.get(10L)).containsExactly("혜인");
  }

  @Test
  void resolveNames_는_전체와_점유슬롯_제외한_잔여를_한_번에_정렬해_반환한다() {
    given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
        .willReturn(
            List.of(
                buncheolMember(801L, 10L, 2001L),
                buncheolMember(802L, 10L, 2002L),
                buncheolMember(803L, 10L, 2003L)));
    given(groupMemberRepository.findAllByIds(List.of(2001L, 2002L, 2003L)))
        .willReturn(
            List.of(groupMember(2001L, "하니"), groupMember(2002L, "민지"), groupMember(2003L, "혜인")));

    // 슬롯 802(민지)는 활성 참여로 점유됨 → available 에서만 제외, all 에는 포함.
    BuncheolMemberNameResolver.MemberNames result =
        resolver.resolveNames(List.of(10L), Set.of(802L));

    assertThat(result.all().get(10L)).containsExactly("하니", "민지", "혜인");
    assertThat(result.available().get(10L)).containsExactly("하니", "혜인");
  }

  @Test
  void 코드_참여_슬롯은_비어_있어도_잔여에서_제외된다() {
    BuncheolMember codeSlot = buncheolMember(802L, 10L, 2002L);
    setField(codeSlot, "accessType", BuncheolMemberAccessType.CODE_ONLY);
    given(buncheolMemberRepository.findAllByBuncheolIds(List.of(10L)))
        .willReturn(List.of(buncheolMember(801L, 10L, 2001L), codeSlot));
    given(groupMemberRepository.findAllByIds(List.of(2001L, 2002L)))
        .willReturn(List.of(groupMember(2001L, "하니"), groupMember(2002L, "민지")));

    BuncheolMemberNameResolver.MemberNames result =
        resolver.resolveNames(List.of(10L), Set.of());

    assertThat(result.all().get(10L)).containsExactly("하니", "민지");
    assertThat(result.available().get(10L)).containsExactly("하니");
  }

  private BuncheolMember buncheolMember(Long id, Long buncheolId, Long memberId) {
    BuncheolMember bm = newInstance(BuncheolMember.class);
    setField(bm, "id", id);
    setField(bm, "buncheolId", buncheolId);
    setField(bm, "memberId", memberId);
    return bm;
  }

  private GroupMember groupMember(Long id, String name) {
    GroupMember gm = newInstance(GroupMember.class);
    setField(gm, "id", id);
    setField(gm, "name", name);
    return gm;
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
