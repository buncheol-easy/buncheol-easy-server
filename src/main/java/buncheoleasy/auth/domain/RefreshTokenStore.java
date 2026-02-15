package buncheoleasy.auth.domain;

/**
 * Refresh Token 저장소 인터페이스
 */
public interface RefreshTokenStore {

    void save(Long userId, String token, long expirationTime);

    void delete(Long userId);

    void verify(Long userId, String token);
}
