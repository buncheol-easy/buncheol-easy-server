package buncheoleasy.global.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 호출 제한 키로 쓸 클라이언트 IP 해석. 의견 보내기와 관리자 로그인이 공유한다.
 *
 * <p>{@code X-Forwarded-For} 의 <b>첫</b> 항목은 클라이언트가 보낸 값이다 — Nginx 의 {@code $proxy_add_x_forwarded_for}
 * 는 뒤에 실제 peer 를 덧붙일 뿐 앞을 덮지 않으므로, 첫 항목을 쓰면 헤더 조작으로 키를 매 요청 바꿔 제한을 통째로 우회할 수 있다. 그래서 Nginx 가
 * 덮어쓰는 {@code X-Real-IP} 를 우선하고, 없으면 XFF 의 <b>마지막</b>(가장 가까운 프록시가 본 peer) 항목을 쓴다.
 *
 * <p>⚠️ 브라우저 트래픽은 프론트(Next.js) 프록시를 거쳐 오므로 여기서 해석되는 IP 가 프록시 IP 하나로 수렴할 수 있다. IP 기반 제한은 그 경우
 * "개인당 한도"가 아니라 "전체 예산"으로 동작하므로, 호출자는 이를 감안해 한도를 잡거나 다른 축(회원 ID·로그인 ID)의 제한과 함께 써야 한다.
 */
public final class ClientIpResolver {

  /** Nginx 가 {@code $remote_addr} 로 <b>덮어쓰는</b> 헤더 — 클라이언트가 보낸 값은 무조건 대체되므로 위조할 수 없다. */
  private static final String REAL_IP_HEADER = "X-Real-IP";

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  /** Redis 키 길이 상한. IPv6 최대 표기(45자)면 충분하며, 조작된 긴 헤더로 키가 비대해지는 것을 막는다. */
  private static final int MAX_CLIENT_IP_LENGTH = 45;

  private static final String UNKNOWN = "unknown";

  private ClientIpResolver() {}

  public static String resolve(final HttpServletRequest request) {
    String realIp = request.getHeader(REAL_IP_HEADER);
    if (realIp != null && !realIp.isBlank()) {
      return truncate(realIp.trim());
    }

    String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
    if (forwardedFor == null || forwardedFor.isBlank()) {
      return truncate(request.getRemoteAddr());
    }
    String[] hops = forwardedFor.split(",");
    return truncate(hops[hops.length - 1].trim());
  }

  private static String truncate(final String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return UNKNOWN;
    }
    return clientIp.length() <= MAX_CLIENT_IP_LENGTH
        ? clientIp
        : clientIp.substring(0, MAX_CLIENT_IP_LENGTH);
  }
}
