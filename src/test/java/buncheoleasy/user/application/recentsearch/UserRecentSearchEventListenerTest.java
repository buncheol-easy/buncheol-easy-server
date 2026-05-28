package buncheoleasy.user.application.recentsearch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import buncheoleasy.buncheol.application.BuncheolSearchedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRecentSearchEventListener 단위 테스트")
class UserRecentSearchEventListenerTest {

  private static final Long USER_ID = 1L;
  private static final String KEYWORD = "ive원영";

  @InjectMocks private UserRecentSearchEventListener listener;

  @Mock private UserRecentSearchCommandService commandService;

  @Test
  void 이벤트의_keyword_를_그대로_저장한다() {
    listener.onSearched(new BuncheolSearchedEvent(USER_ID, KEYWORD));

    then(commandService).should().record(USER_ID, KEYWORD);
  }

  @Test
  void DB_장애를_던져도_삼킨다() {
    willThrow(new DataAccessResourceFailureException("DB 일시 장애"))
        .given(commandService)
        .record(USER_ID, KEYWORD);

    assertThatCode(() -> listener.onSearched(new BuncheolSearchedEvent(USER_ID, KEYWORD)))
        .doesNotThrowAnyException();
  }

  @Test
  void DataAccessException_이_아닌_예외는_그대로_전파된다() {
    willThrow(new RuntimeException("코드 결함 — 모니터링이 잡도록 전파"))
        .given(commandService)
        .record(USER_ID, KEYWORD);

    assertThatThrownBy(() -> listener.onSearched(new BuncheolSearchedEvent(USER_ID, KEYWORD)))
        .isInstanceOf(RuntimeException.class);
  }
}
