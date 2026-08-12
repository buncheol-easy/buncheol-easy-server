package buncheoleasy.auth.infrastructure.jwt;

/**
 * access token 페이로드. 유저 토큰이면 {@code userId} 는 users.id 이고 {@code role} 은 null, 관리자 토큰이면 admins.id 와
 * {@code "ADMIN"} 이다. 두 id 공간은 겹칠 수 있으므로 인가는 role 로 구분해야 한다.
 *
 * <p>{@code impersonated} 는 관리자가 문의 재현용으로 발급한 유저 토큰(role 은 그대로 null — 유저 API 를 호출해야 하므로)임을
 * 표시한다. 이 토큰으로는 refresh 세션을 지우는 로그아웃을 막아, 재현 대상 유저가 자기 기기에서 튕겨 나가지 않게 한다.
 */
public record AccessTokenClaims(Long userId, String role, boolean impersonated) {}
