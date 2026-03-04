package buncheoleasy.user.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.application.UserService;
import buncheoleasy.user.dto.response.UserProfileResponse;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("UserController 테스트")
class UserControllerTest {

  private static final Long USER_ID = 1L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor mockAuth() {
    return (MockHttpServletRequest request) -> {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
      return request;
    };
  }

  @Test
  void 내_프로필_조회가_성공하면_200과_응답본문을_반환한다() throws Exception {
    given(userService.getUserProfile(USER_ID))
        .willReturn(UserProfileResponse.of("KAKAO", "test@example.com", "테스트닉", "01012345678"));

    mockMvc
        .perform(get("/v1/users/me").with(mockAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("KAKAO"))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.nickname").value("테스트닉"))
        .andExpect(jsonPath("$.phoneNumber").value("01012345678"));
  }

  @Test
  void 프로필_수정_요청_검증에_실패하면_400과_표준_에러코드를_반환한다() throws Exception {
    mockMvc
        .perform(
            put("/v1/users/me")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"테스트@유저\",\"phoneNumber\":\"01012345678\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
  }

  @Test
  void 회원탈퇴_중_BusinessException이_발생하면_해당_HTTP_상태코드로_매핑된다() throws Exception {
    willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND)).given(userService).withdraw(USER_ID);

    mockMvc
        .perform(delete("/v1/users/me").with(mockAuth()))
        .andExpect(status().isNotFound())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString(ErrorCode.USER_NOT_FOUND.getCode())));
  }

  @Test
  void 회원탈퇴가_성공하면_204를_반환한다() throws Exception {
    mockMvc.perform(delete("/v1/users/me").with(mockAuth())).andExpect(status().isNoContent());

    then(userService).should().withdraw(USER_ID);
  }
}
