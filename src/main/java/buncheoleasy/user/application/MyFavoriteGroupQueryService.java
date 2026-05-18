package buncheoleasy.user.application;

import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupRepository;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
import buncheoleasy.user.dto.response.MyFavoriteGroupResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyFavoriteGroupQueryService {

  private final UserFavoriteGroupRepository userFavoriteGroupRepository;
  private final GroupRepository groupRepository;

  @Transactional(readOnly = true)
  public List<MyFavoriteGroupResponse> getMyFavoriteGroups(final Long userId) {
    List<UserFavoriteGroup> favorites =
        userFavoriteGroupRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
    if (favorites.isEmpty()) {
      return List.of();
    }

    List<Long> groupIds = favorites.stream().map(UserFavoriteGroup::getGroupId).toList();
    Map<Long, Group> groupById =
        groupRepository.findAllByIds(groupIds).stream()
            .collect(Collectors.toMap(Group::getId, g -> g));

    return favorites.stream()
        .map(
            f -> {
              Group group = groupById.get(f.getGroupId());
              if (group == null) {
                // 그룹이 hard delete 된 비정상 케이스 (FK CASCADE 가 막아주므로 정상 흐름엔 없음).
                return null;
              }
              return new MyFavoriteGroupResponse(
                  f.getId(), group.getId(), group.getName(), group.getImage());
            })
        .filter(Objects::nonNull)
        .toList();
  }
}
