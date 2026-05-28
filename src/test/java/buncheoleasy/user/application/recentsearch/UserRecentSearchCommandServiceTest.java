package buncheoleasy.user.application.recentsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import buncheoleasy.user.domain.recentsearch.UserRecentSearch;
import buncheoleasy.user.domain.recentsearch.UserRecentSearchRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRecentSearchCommandService 단위 테스트")
class UserRecentSearchCommandServiceTest {

  private static final Long USER_ID = 1L;
  private static final String KEYWORD = "뉴진스";

  @InjectMocks private UserRecentSearchCommandService commandService;

  @Mock private UserRecentSearchRepository repository;

  @Test
  void record_는_기존_삭제_후_저장하고_초과분을_정리한다() {
    given(repository.findIdsToTrim(USER_ID, 7)).willReturn(List.of(99L, 100L));

    commandService.record(USER_ID, KEYWORD);

    InOrder inOrder = Mockito.inOrder(repository);
    inOrder.verify(repository).deleteByUserIdAndKeyword(USER_ID, KEYWORD);
    inOrder.verify(repository).save(any(UserRecentSearch.class));
    inOrder.verify(repository).findIdsToTrim(USER_ID, 7);
    inOrder.verify(repository).deleteAllByIdIn(List.of(99L, 100L));
  }

  @Test
  void 저장되는_엔티티는_userId_와_keyword_가_채워진다() {
    given(repository.findIdsToTrim(any(), eq(7))).willReturn(List.of());
    ArgumentCaptor<UserRecentSearch> captor = ArgumentCaptor.forClass(UserRecentSearch.class);

    commandService.record(USER_ID, KEYWORD);

    Mockito.verify(repository).save(captor.capture());
    UserRecentSearch saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getKeyword()).isEqualTo(KEYWORD);
  }
}
