package buncheoleasy.push.domain;

import java.util.List;

/** 앱 푸시 기기 저장소. 삭제·upsert 는 {@code @Modifying} 문이라 호출 측 트랜잭션이 필요하다. */
public interface PushDeviceRepository {

  /**
   * 토큰 기준 upsert. 없으면 넣고, 있으면 주인·플랫폼·환경을 덮고 {@code updated_at} 을 지금으로 바꾼다(같은 기기로 다른 계정에 로그인한 경우).
   * 단일 문이라 같은 토큰을 동시에 등록해도 유니크 충돌이 나지 않는다.
   */
  void upsert(PushDevice device);

  List<PushDevice> findAllByUserId(Long userId);

  /** 회원의 기기를 최근 등록({@code updated_at}) 순으로 세워 {@code keep} 개째 뒤의 id 를 돌려준다. 기기 수 상한 정리용. */
  List<Long> findIdsToTrim(Long userId, int keep);

  /** 빈 리스트도 받는다. */
  void deleteAllByIdIn(List<Long> ids);

  /** 본인 기기 토큰 1건 삭제. 남의 토큰·없는 토큰은 0행 — 존재 여부가 응답으로 새지 않게 0행도 성공으로 다룬다(멱등). */
  int deleteOwnedByToken(Long userId, String token);

  /** 애플이 무효라고 답한 토큰 삭제. 토큰 자체가 죽었으므로 주인과 무관하게 지운다. */
  int deleteByToken(String token);

  /** 회원 탈퇴 시 그 회원의 기기를 모두 지운다. */
  int deleteAllByUserId(Long userId);
}
