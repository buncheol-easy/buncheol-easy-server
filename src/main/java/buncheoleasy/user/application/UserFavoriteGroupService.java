package buncheoleasy.user.application;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserFavoriteGroupService {

  private final UserFavoriteGroupRepository userFavoriteGroupRepository;
  private final GroupDomainService groupDomainService;

  @Transactional
  public void addFavoriteGroup(final Long userId, final Long groupId) {
    groupDomainService.validateGroupExists(groupId);

    if (userFavoriteGroupRepository.existsByUserIdAndGroupId(userId, groupId)) {
      throw new BusinessException(ErrorCode.FAVORITE_GROUP_ALREADY_EXISTS);
    }

    userFavoriteGroupRepository.save(UserFavoriteGroup.create(userId, groupId));
  }

  @Transactional
  public void removeFavoriteGroup(final Long userId, final Long groupId) {
    int deleted = userFavoriteGroupRepository.deleteByUserIdAndGroupId(userId, groupId);
    if (deleted == 0) {
      throw new BusinessException(ErrorCode.FAVORITE_GROUP_NOT_FOUND);
    }
  }
}
