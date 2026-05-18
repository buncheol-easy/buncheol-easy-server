package buncheoleasy.user.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.user.domain.favorite.UserFavoriteGroup;
import buncheoleasy.user.domain.favorite.UserFavoriteGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserFavoriteGroupService 단위 테스트")
class UserFavoriteGroupServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long GROUP_ID = 100L;

  @InjectMocks private UserFavoriteGroupService userFavoriteGroupService;

  @Mock private UserFavoriteGroupRepository userFavoriteGroupRepository;
  @Mock private GroupDomainService groupDomainService;

  @Nested
  @DisplayName("최애 등록 테스트")
  class AddFavoriteTest {

    @Test
    void 처음_등록하는_그룹이면_저장한다() {
      willDoNothing().given(groupDomainService).validateGroupExists(GROUP_ID);
      given(userFavoriteGroupRepository.existsByUserIdAndGroupId(USER_ID, GROUP_ID))
          .willReturn(false);
      given(userFavoriteGroupRepository.countByUserId(USER_ID)).willReturn(0);

      userFavoriteGroupService.addFavoriteGroup(USER_ID, GROUP_ID);

      then(userFavoriteGroupRepository).should().save(any(UserFavoriteGroup.class));
    }

    @Test
    void 이미_5개를_등록한_사용자가_추가하면_400_예외가_발생한다() {
      willDoNothing().given(groupDomainService).validateGroupExists(GROUP_ID);
      given(userFavoriteGroupRepository.existsByUserIdAndGroupId(USER_ID, GROUP_ID))
          .willReturn(false);
      given(userFavoriteGroupRepository.countByUserId(USER_ID)).willReturn(5);

      assertThatThrownBy(() -> userFavoriteGroupService.addFavoriteGroup(USER_ID, GROUP_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.FAVORITE_GROUP_LIMIT_EXCEEDED);

      then(userFavoriteGroupRepository).should(never()).save(any());
    }

    @Test
    void 존재하지_않는_그룹이면_예외가_발생한다() {
      willThrow(new BusinessException(ErrorCode.GROUP_NOT_FOUND))
          .given(groupDomainService)
          .validateGroupExists(GROUP_ID);

      assertThatThrownBy(() -> userFavoriteGroupService.addFavoriteGroup(USER_ID, GROUP_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.GROUP_NOT_FOUND);

      then(userFavoriteGroupRepository).should(never()).save(any());
    }

    @Test
    void 이미_최애로_등록된_그룹이면_409_예외가_발생한다() {
      willDoNothing().given(groupDomainService).validateGroupExists(GROUP_ID);
      given(userFavoriteGroupRepository.existsByUserIdAndGroupId(USER_ID, GROUP_ID))
          .willReturn(true);

      assertThatThrownBy(() -> userFavoriteGroupService.addFavoriteGroup(USER_ID, GROUP_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.FAVORITE_GROUP_ALREADY_EXISTS);

      then(userFavoriteGroupRepository).should(never()).save(any());
    }
  }

  @Nested
  @DisplayName("최애 해제 테스트")
  class RemoveFavoriteTest {

    @Test
    void 본인_최애가_존재하면_삭제한다() {
      given(userFavoriteGroupRepository.deleteByUserIdAndGroupId(USER_ID, GROUP_ID)).willReturn(1);

      assertThatCode(() -> userFavoriteGroupService.removeFavoriteGroup(USER_ID, GROUP_ID))
          .doesNotThrowAnyException();
    }

    @Test
    void 최애가_없으면_404_예외가_발생한다() {
      given(userFavoriteGroupRepository.deleteByUserIdAndGroupId(USER_ID, GROUP_ID)).willReturn(0);

      assertThatThrownBy(() -> userFavoriteGroupService.removeFavoriteGroup(USER_ID, GROUP_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.FAVORITE_GROUP_NOT_FOUND);
    }
  }
}
