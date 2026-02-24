package buncheoleasy.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import buncheoleasy.auth.infrastructure.response.ErrorResponseWriter;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationEntryPoint 단위 테스트")
class JwtAuthenticationEntryPointTest {

    @Mock
    private ErrorResponseWriter errorResponseWriter;

    @Nested
    @DisplayName("commence 테스트")
    class CommenceTest {

        @Test
        void 요청_속성에_BusinessException이_있으면_해당_에러코드로_응답한다() throws Exception {
            // given
            JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(errorResponseWriter);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setAttribute(
                    JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE,
                    new BusinessException(ErrorCode.AUTH_EXPIRED_TOKEN)
            );

            // when
            entryPoint.commence(request, response, new BadCredentialsException("failed"));

            // then
            ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
            then(errorResponseWriter).should().write(eq(request), eq(response), captor.capture());
            ProblemDetail problemDetail = captor.getValue();
            assertThat(problemDetail.getStatus()).isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN.getHttpStatus().value());
            assertThat(problemDetail.getProperties().get("code")).isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN.getCode());
        }

        @Test
        void 요청_속성에_예외가_없으면_AUTH_INVALID_TOKEN으로_응답한다() throws Exception {
            // given
            JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(errorResponseWriter);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            entryPoint.commence(request, response, new BadCredentialsException("failed"));

            // then
            ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
            then(errorResponseWriter).should().write(eq(request), eq(response), captor.capture());
            ProblemDetail problemDetail = captor.getValue();
            assertThat(problemDetail.getStatus()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN.getHttpStatus().value());
            assertThat(problemDetail.getProperties().get("code")).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN.getCode());
        }

        @Test
        void 요청_속성에_BusinessException이_아닌_예외가_있어도_AUTH_INVALID_TOKEN으로_응답한다() throws Exception {
            // given
            JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(errorResponseWriter);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE, new RuntimeException("unexpected"));

            // when
            entryPoint.commence(request, response, new BadCredentialsException("failed"));

            // then
            ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
            then(errorResponseWriter).should().write(eq(request), eq(response), captor.capture());
            ProblemDetail problemDetail = captor.getValue();
            assertThat(problemDetail.getStatus()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN.getHttpStatus().value());
            assertThat(problemDetail.getProperties().get("code")).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN.getCode());
        }
    }
}
