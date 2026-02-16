package buncheoleasy.auth.infrastructure.response;

import buncheoleasy.auth.dto.TokenPair;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * OAuth2 로그인 성공 시 토큰 응답을 작성하는 Writer
 */
@Component
public class TokenResponseWriter extends FilterJsonResponseWriter {

    public TokenResponseWriter(final ObjectMapper objectMapper) {
        super(objectMapper);
    }

    public void write(final HttpServletResponse response, final TokenPair token) throws IOException {
        writeJson(response, HttpServletResponse.SC_OK, token);
    }
}
