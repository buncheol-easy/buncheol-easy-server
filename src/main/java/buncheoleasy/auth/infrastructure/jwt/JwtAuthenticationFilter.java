package buncheoleasy.auth.infrastructure.jwt;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EXCEPTION_ATTRIBUTE = "exception";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            final String accessToken = extractAccessToken(request);

            if (StringUtils.hasText(accessToken)) {
                authenticateWithToken(accessToken);
            }
        } catch (final BusinessException exception) {
            handleAuthenticationException(request, exception);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 요청 헤더에서 액세스 토큰을 추출합니다.
     */
    private String extractAccessToken(final HttpServletRequest request) {
        final String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(bearerToken)) {
            return null;
        }

        validateBearerFormat(bearerToken);

        final String token = bearerToken.substring(BEARER_PREFIX.length()).trim();
        validateTokenPresence(token);

        return token;
    }

    private void validateBearerFormat(final String bearerToken) {
        if (!bearerToken.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private void validateTokenPresence(final String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private void authenticateWithToken(final String accessToken) {
        final Long userId = jwtTokenProvider.parseUserIdFromAccessToken(accessToken);

        final Authentication authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                Collections.emptyList()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void handleAuthenticationException(
            final HttpServletRequest request,
            final BusinessException exception
    ) {
        request.setAttribute(EXCEPTION_ATTRIBUTE, exception);
    }
}
