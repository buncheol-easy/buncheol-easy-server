package buncheoleasy.inbox.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.inbox.application.NoticeCommandService;
import buncheoleasy.inbox.application.image.ImageFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("NoticeController 테스트")
class NoticeControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NoticeCommandService noticeCommandService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  private MockMultipartFile requestPart(final String json) {
    return new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
  }

  @Test
  void 비어있는_이미지_파트는_null_ImageFile_로_전달된다() throws Exception {
    given(noticeCommandService.createNotice(any(), any(), any())).willReturn(1L);
    MockMultipartFile emptyImage =
        new MockMultipartFile("image", "x.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

    mockMvc
        .perform(
            multipart("/v1/notices")
                .file(requestPart("{\"title\":\"제목\",\"description\":\"설명\",\"pinned\":false}"))
                .file(emptyImage))
        .andExpect(status().isCreated());

    ArgumentCaptor<ImageFile> imageCaptor = ArgumentCaptor.forClass(ImageFile.class);
    ArgumentCaptor<ImageFile> bannerCaptor = ArgumentCaptor.forClass(ImageFile.class);
    verify(noticeCommandService)
        .createNotice(any(), imageCaptor.capture(), bannerCaptor.capture());
    assertThat(imageCaptor.getValue()).isNull();
    assertThat(bannerCaptor.getValue()).isNull();
  }

  @Test
  void 채워진_이미지_파트는_ImageFile_로_변환되어_전달된다() throws Exception {
    given(noticeCommandService.createNotice(any(), any(), any())).willReturn(1L);
    MockMultipartFile image =
        new MockMultipartFile(
            "image", "notice.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});

    mockMvc
        .perform(
            multipart("/v1/notices")
                .file(requestPart("{\"title\":\"제목\",\"description\":\"설명\",\"pinned\":false}"))
                .file(image))
        .andExpect(status().isCreated());

    ArgumentCaptor<ImageFile> imageCaptor = ArgumentCaptor.forClass(ImageFile.class);
    verify(noticeCommandService).createNotice(any(), imageCaptor.capture(), any());
    assertThat(imageCaptor.getValue()).isNotNull();
    assertThat(imageCaptor.getValue().originalFilename()).isEqualTo("notice.jpg");
  }

  @Test
  void 배너_제목만_있고_배너_이미지가_없으면_400_INB_006_을_반환한다() throws Exception {
    // 배너 JSON 만 있고 bannerImage 파트가 없는 실수 시나리오. 서비스가 던지는 INB-006 이 400 으로 매핑되는지 확인.
    willThrow(new BusinessException(ErrorCode.NOTICE_BANNER_INCOMPLETE))
        .given(noticeCommandService)
        .createNotice(any(), any(), any());

    mockMvc
        .perform(
            multipart("/v1/notices")
                .file(
                    requestPart(
                        "{\"title\":\"제목\",\"description\":\"설명\",\"pinned\":false,"
                            + "\"banner\":{\"title\":\"여름 이벤트\"}}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INB-006"));
  }
}
