package buncheoleasy.auth.infrastructure.response;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@DisplayName("ErrorResponseWriter 단위 테스트")
class ErrorResponseWriterTest {

  private final ErrorResponseWriter errorResponseWriter =
      new ErrorResponseWriter(new ObjectMapper());

  @Test
  void ProblemDetail_응답에_instance_URI를_추가하여_JSON으로_작성한다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    ProblemDetail problemDetail = ErrorCode.AUTH_INVALID_TOKEN.toProblemDetail();

    // when
    errorResponseWriter.write(request, response, problemDetail);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

    String body = response.getContentAsString();
    assertThat(body)
        .contains("\"instance\":\"/v1/users/me\"")
        .contains(ErrorCode.AUTH_INVALID_TOKEN.getCode())
        .contains(ErrorCode.AUTH_INVALID_TOKEN.getMessage());
  }
}
