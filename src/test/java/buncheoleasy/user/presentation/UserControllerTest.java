package buncheoleasy.user.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import buncheoleasy.user.dto.request.BankAccountRequest;
import buncheoleasy.user.dto.response.NicknameDuplicateResponse;
import buncheoleasy.user.dto.response.ProfileStatusResponse;
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
        .willReturn(
            UserProfileResponse.of("KAKAO", "test@example.com", "테스트닉", "01012345678", null));

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
        .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
  }

  @Test
  void 회원탈퇴_중_BusinessException이_발생하면_해당_HTTP_상태코드로_매핑된다() throws Exception {
    willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND)).given(userService).withdraw(USER_ID);

    mockMvc
        .perform(delete("/v1/users/me").with(mockAuth()))
        .andExpect(status().isNotFound())
        .andExpect(content().string(containsString(ErrorCode.USER_NOT_FOUND.getCode())));
  }

  @Test
  void 회원탈퇴가_성공하면_204를_반환한다() throws Exception {
    mockMvc.perform(delete("/v1/users/me").with(mockAuth())).andExpect(status().isNoContent());

    then(userService).should().withdraw(USER_ID);
  }

  @Test
  void 정산_계좌_등록이_성공하면_204를_반환한다() throws Exception {
    mockMvc
        .perform(
            put("/v1/users/me/bank-account")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bank\":\"국민은행\",\"account\":\"123456789012\",\"holder\":\"홍길동\"}"))
        .andExpect(status().isNoContent());

    then(userService).should().updateBankAccount(eq(USER_ID), any(BankAccountRequest.class));
  }

  @Test
  void 정산_계좌_등록_요청에_빈_값이_포함되면_400을_반환한다() throws Exception {
    mockMvc
        .perform(
            put("/v1/users/me/bank-account")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bank\":\"\",\"account\":\"123\",\"holder\":\"홍길동\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
  }

  @Test
  void 프로필_완료_여부_조회가_성공하면_200과_상태를_반환한다() throws Exception {
    given(userService.getProfileStatus(USER_ID)).willReturn(ProfileStatusResponse.of(true));

    mockMvc
        .perform(get("/v1/users/me/profile/status").with(mockAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileCompleted").value(true));
  }

  @Test
  void 닉네임_중복_조회가_성공하면_200과_duplicated를_반환한다() throws Exception {
    given(userService.checkNicknameDuplicate(USER_ID, "새닉"))
        .willReturn(NicknameDuplicateResponse.of(false));

    mockMvc
        .perform(get("/v1/users/nickname/duplicate").param("nickname", "새닉").with(mockAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.duplicated").value(false));
  }

  @Test
  void 닉네임_중복_조회_시_형식_위반이면_400을_반환한다() throws Exception {
    willThrow(new BusinessException(ErrorCode.USER_NICKNAME_FORMAT_INVALID))
        .given(userService)
        .checkNicknameDuplicate(USER_ID, "잘못된@닉");

    mockMvc
        .perform(get("/v1/users/nickname/duplicate").param("nickname", "잘못된@닉").with(mockAuth()))
        .andExpect(status().isBadRequest())
        .andExpect(
            content().string(containsString(ErrorCode.USER_NICKNAME_FORMAT_INVALID.getCode())));
  }

  @Test
  void 닉네임_중복_조회_시_nickname_파라미터_누락이면_400을_반환한다() throws Exception {
    mockMvc
        .perform(get("/v1/users/nickname/duplicate").with(mockAuth()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 정산_계좌번호에_숫자_외_문자가_포함되면_400을_반환한다() throws Exception {
    mockMvc
        .perform(
            put("/v1/users/me/bank-account")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bank\":\"국민은행\",\"account\":\"123-456-789012\",\"holder\":\"홍길동\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString(ErrorCode.INVALID_INPUT_VALUE.getCode())));
  }
}
