package buncheoleasy.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("ClientIpResolver 단위 테스트")
class ClientIpResolverTest {

  private MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.1");
    return request;
  }

  @Test
  void X_Real_IP_가_있으면_그_값을_쓴다() {
    // Nginx 가 $remote_addr 로 덮어쓰는 헤더라 클라이언트가 위조할 수 없다.
    MockHttpServletRequest request = request();
    request.addHeader("X-Real-IP", "203.0.113.9");
    request.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2");

    assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void X_Real_IP_가_없으면_XFF_의_마지막_홉을_쓴다() {
    // XFF 의 첫 항목은 클라이언트가 보낸 값이라 조작으로 키를 매 요청 바꿔 제한을 우회할 수 있다.
    // 마지막 항목이 가장 가까운 프록시가 실제로 본 peer 다.
    MockHttpServletRequest request = request();
    request.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 203.0.113.9");

    assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void X_Real_IP_가_공백뿐이면_XFF_로_넘어간다() {
    MockHttpServletRequest request = request();
    request.addHeader("X-Real-IP", "   ");
    request.addHeader("X-Forwarded-For", "203.0.113.9");

    assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void 두_헤더가_모두_없으면_remoteAddr_를_쓴다() {
    assertThat(ClientIpResolver.resolve(request())).isEqualTo("10.0.0.1");
  }

  @Test
  void remoteAddr_까지_비면_unknown_으로_떨어진다() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(null);

    assertThat(ClientIpResolver.resolve(request)).isEqualTo("unknown");
  }

  @Test
  void 조작된_긴_헤더는_45자로_잘라_키가_비대해지는_것을_막는다() {
    // IPv6 최대 표기(45자)면 충분하다.
    MockHttpServletRequest request = request();
    request.addHeader("X-Real-IP", "9".repeat(200));

    assertThat(ClientIpResolver.resolve(request)).hasSize(45);
  }
}
