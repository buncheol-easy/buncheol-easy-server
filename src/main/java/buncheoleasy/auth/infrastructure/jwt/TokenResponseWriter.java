package buncheoleasy.auth.infrastructure.jwt;

import buncheoleasy.auth.dto.Tokens;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface TokenResponseWriter {

    void write(HttpServletResponse response, Tokens tokens) throws IOException;
}
