package buncheoleasy.auth.infrastructure.jwt;

import buncheoleasy.auth.dto.Tokens;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JsonTokenResponseWriter implements TokenResponseWriter {

    private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    private final ObjectMapper objectMapper;

    @Override
    public void write(HttpServletResponse response, Tokens tokens) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(CONTENT_TYPE_JSON);
        objectMapper.writeValue(response.getWriter(), tokens);
    }
}
