package buncheoleasy.auth.infrastructure.response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 필터에서 발생한 예외를 ProblemDetail 형식으로 응답하는 Writer
 */
@Component
public class ErrorResponseWriter extends FilterJsonResponseWriter {

    public ErrorResponseWriter(final ObjectMapper objectMapper) {
        super(objectMapper);
    }

    public void write(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final ProblemDetail problemDetail
    ) throws IOException {
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        writeJson(response, problemDetail.getStatus(), problemDetail);
    }
}
