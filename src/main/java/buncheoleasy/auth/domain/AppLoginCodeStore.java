package buncheoleasy.auth.domain;

import java.time.Duration;
import java.util.Optional;

/** 앱 로그인 일회용 코드 저장소. */
public interface AppLoginCodeStore {

  void save(String code, AppLoginGrant grant, Duration timeToLive);

  /** 코드를 꺼내면서 지운다. 같은 코드를 두 번 꺼내면 두 번째는 비어 있어야 한다(동시 요청 포함). 만료·없는 코드도 비어 있다. */
  Optional<AppLoginGrant> consume(String code);
}
