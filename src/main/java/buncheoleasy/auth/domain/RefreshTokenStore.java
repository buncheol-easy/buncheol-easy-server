package buncheoleasy.auth.domain;

/** Refresh Token 저장소 인터페이스. 회원마다 {@link LoginClient} 별로 한 칸씩 둔다. */
public interface RefreshTokenStore {

  void save(Long userId, LoginClient client, String token, long expirationTime);

  void delete(Long userId, LoginClient client);

  /** 모든 칸을 지운다 — 탈퇴처럼 어느 기기에서도 이어지면 안 될 때. */
  void deleteAll(Long userId);

  void verify(Long userId, LoginClient client, String token);
}
