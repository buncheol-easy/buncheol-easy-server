package buncheoleasy.auth.infrastructure.response;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.auth.TokenPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@DisplayName("TokenResponseWriter 단위 테스트")
class TokenResponseWriterTest {

    private final TokenResponseWriter tokenResponseWriter = new TokenResponseWriter(new ObjectMapper());

    @Test
    void 토큰_응답을_JSON으로_작성한다() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        TokenPair tokenPair = new TokenPair("access-token", "refresh-token");

        // when
        tokenResponseWriter.write(response, tokenPair);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getContentAsString())
                .contains("\"accessToken\":\"access-token\"")
                .contains("\"refreshToken\":\"refresh-token\"");
    }
}
