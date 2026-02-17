package buncheoleasy.auth.infrastructure.jwt;

import buncheoleasy.auth.infrastructure.response.ErrorResponseWriter;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * JWT 인증 실패 시 401 응답을 처리하는 클래스
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(final HttpServletRequest request, final HttpServletResponse response,
                         final AuthenticationException authException) throws IOException {
        final ErrorCode errorCode = extractErrorCode(request);
        final ProblemDetail problemDetail = errorCode.toProblemDetail();
        errorResponseWriter.write(request, response, problemDetail);
    }

    private ErrorCode extractErrorCode(final HttpServletRequest request) {
        final Object attribute = request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE);

        if (attribute instanceof BusinessException businessException) {
            return businessException.getErrorCode();
        }

        return ErrorCode.AUTH_INVALID_TOKEN;
    }
}
