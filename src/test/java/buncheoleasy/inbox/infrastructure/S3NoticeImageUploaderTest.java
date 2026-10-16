package buncheoleasy.inbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.inbox.application.image.ImageFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** #224 — S3 가 이 값으로 파일을 내준다. 공지 본문과 홈 배너가 같은 업로드 경로를 거치므로 둘 다 고정한다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3NoticeImageUploader 저장 형식 테스트")
class S3NoticeImageUploaderTest {

  @Mock private S3Client s3Client;

  @InjectMocks private S3NoticeImageUploader uploader;

  @BeforeEach
  void setUp() {
    setField(uploader, "bucketName", "test-bucket");
    setField(uploader, "origin", "https://test-bucket.example.com");
  }

  @Test
  void 본문_이미지는_HTML_로_보내도_image_png_로_저장한다() {
    ImageFile imageFile = new ImageFile("evil.png", "text/html", new byte[] {1, 2, 3});

    String url = uploader.uploadNoticeImageAndGetUrl(7L, imageFile);

    PutObjectRequest request = capturePutObjectRequest();
    assertThat(request.contentType()).isEqualTo("image/png");
    assertThat(request.key()).startsWith("notices/7/images/").endsWith(".png");
    assertThat(url).isEqualTo("https://test-bucket.example.com/" + request.key());
  }

  @Test
  void 배너_이미지는_SVG_로_보내도_image_png_로_저장한다() {
    ImageFile imageFile = new ImageFile("banner.png", "image/svg+xml", new byte[] {1, 2, 3});

    String url = uploader.uploadBannerImageAndGetUrl(7L, imageFile);

    PutObjectRequest request = capturePutObjectRequest();
    assertThat(request.contentType()).isEqualTo("image/png");
    assertThat(request.key()).startsWith("notices/7/banner/").endsWith(".png");
    assertThat(url).isEqualTo("https://test-bucket.example.com/" + request.key());
  }

  private PutObjectRequest capturePutObjectRequest() {
    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    then(s3Client).should().putObject(captor.capture(), any(RequestBody.class));
    return captor.getValue();
  }
}
