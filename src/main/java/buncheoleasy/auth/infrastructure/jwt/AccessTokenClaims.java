package buncheoleasy.auth.infrastructure.jwt;

/**
 * access token 페이로드. 유저 토큰이면 {@code userId} 는 users.id 이고 {@code role} 은 null, 관리자 토큰이면 admins.id 와
 * {@code AdminRole.name()} 이다. 두 id 공간은 겹칠 수 있으므로 인가는 role 로 구분해야 한다.
 */
public record AccessTokenClaims(Long userId, String role) {}
