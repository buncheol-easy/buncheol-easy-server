package buncheoleasy.user.application.recentsearch;

import buncheoleasy.buncheol.application.BuncheolSearchedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 분철 검색이 커밋되면 별도 트랜잭션 + 비동기 스레드에서 사용자의 최근 검색 이력을 갱신한다.
 *
 * <p>검색 응답 시간에 영향을 주지 않기 위해 DB 일시 장애({@link DataAccessException}) 만 로그로 흡수한다. 그 외 예외는 코드 결함이므로
 * 비동기 스레드에서 전파시켜 모니터링이 잡도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRecentSearchEventListener {

  private final UserRecentSearchCommandService commandService;

  @TransactionalEventListener
  @Async
  public void onSearched(final BuncheolSearchedEvent event) {
    try {
      commandService.record(event.userId(), event.rawKeyword());
    } catch (DataAccessException e) {
      log.warn("최근 검색어 저장 실패 userId={} keyword={}", event.userId(), event.rawKeyword(), e);
    }
  }
}
