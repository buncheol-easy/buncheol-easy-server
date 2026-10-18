package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import buncheoleasy.buncheol.application.image.ImageFile;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("S3BuncheolImageUploader 저장 형식 테스트")
class S3BuncheolImageUploaderTest {

  @Mock private S3Client s3Client;

  @InjectMocks private S3BuncheolImageUploader uploader;

  @BeforeEach
  void setUp() {
    setField(uploader, "bucketName", "test-bucket");
    setField(uploader, "origin", "https://test-bucket.example.com");
  }

  // #224 — S3 가 이 값으로 파일을 내준다. 클라가 text/html 을 실어 보냈어도 png 이름이면 image/png 로 저장해야 한다.
  @Test
  void HTML_로_보낸_png_도_image_png_로_저장한다() {
    ImageFile imageFile = new ImageFile("evil.png", "text/html", new byte[] {1, 2, 3});

    String url = uploader.uploadBuncheolImageAndGetUrl(10L, imageFile);

    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    then(s3Client).should().putObject(captor.capture(), any(RequestBody.class));
    PutObjectRequest request = captor.getValue();
    assertThat(request.contentType()).isEqualTo("image/png");
    assertThat(request.key()).startsWith("buncheols/10/images/").endsWith(".png");
    assertThat(url).isEqualTo("https://test-bucket.example.com/" + request.key());
  }
}
