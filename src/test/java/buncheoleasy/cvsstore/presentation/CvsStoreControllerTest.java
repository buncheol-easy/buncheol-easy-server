package buncheoleasy.cvsstore.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.cvsstore.application.CvsStoreService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.page.CursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("CvsStoreController 테스트")
class CvsStoreControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CvsStoreService cvsStoreService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void keyword_가_100자_초과면_400과_INVALID_INPUT_VALUE를_반환한다() throws Exception {
    // given
    String overLimit = "a".repeat(101);

    // when & then
    mockMvc
        .perform(get("/v1/cvs-stores").queryParam("keyword", overLimit))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

    then(cvsStoreService).shouldHaveNoInteractions();
  }

  @Test
  void 지원하지_않는_브랜드면_400과_CVS_BRAND_INVALID를_반환한다() throws Exception {
    // given
    willThrow(new BusinessException(ErrorCode.CVS_BRAND_INVALID))
        .given(cvsStoreService)
        .searchStores(eq("SEVEN"), isNull(), isNull(), eq(20));

    // when & then
    mockMvc
        .perform(get("/v1/cvs-stores").queryParam("brand", "SEVEN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.CVS_BRAND_INVALID.getCode()));
  }

  @Test
  void 형식이_잘못된_커서면_400과_CURSOR_INVALID를_반환한다() throws Exception {
    // given
    willThrow(new BusinessException(ErrorCode.CURSOR_INVALID))
        .given(cvsStoreService)
        .searchStores(isNull(), isNull(), eq("abc"), eq(20));

    // when & then
    mockMvc
        .perform(get("/v1/cvs-stores").queryParam("cursor", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.CURSOR_INVALID.getCode()));
  }

  @Test
  void 파라미터_없이_호출하면_기본값으로_서비스에_위임한다() throws Exception {
    // given
    given(cvsStoreService.searchStores(null, null, null, 20))
        .willReturn(CursorResponse.empty());

    // when & then
    mockMvc.perform(get("/v1/cvs-stores")).andExpect(status().isOk());

    then(cvsStoreService).should().searchStores(null, null, null, 20);
  }
}
