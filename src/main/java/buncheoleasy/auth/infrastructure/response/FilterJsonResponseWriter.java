package buncheoleasy.auth.infrastructure.response;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/** security 필터에서 JSON 응답을 직접 작성하는 추상 클래스 */
@RequiredArgsConstructor
public abstract class FilterJsonResponseWriter {

  protected final ObjectMapper objectMapper;

  protected void writeJson(final HttpServletResponse response, final int status, final Object data)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    objectMapper.writeValue(response.getWriter(), data);
  }
}
