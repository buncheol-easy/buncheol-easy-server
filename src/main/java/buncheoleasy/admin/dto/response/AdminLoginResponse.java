package buncheoleasy.admin.dto.response;

/**
 * 관리자 로그인 응답. 관리자 토큰은 refresh 없이 access 단독(기본 12시간)이라 만료되면 다시 로그인한다.
 */
public record AdminLoginResponse(String accessToken) {}
