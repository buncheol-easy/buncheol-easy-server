package buncheoleasy.buncheol.application.image;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolImageEventListener 테스트")
class BuncheolImageEventListenerTest {

  private static final Long BUNCHEOL_ID = 10L;

  @Mock private BuncheolImageUploader imageUploader;

  @Mock private BuncheolImageDomainService buncheolImageDomainService;

  @InjectMocks private BuncheolImageEventListener listener;

  private ImageFile imageFile(final String name) {
    return new ImageFile(name, "image/jpeg", new byte[] {1, 2, 3});
  }

  private void givenUploadSucceeds(final ImageFile file, final String url) {
    given(imageUploader.uploadBuncheolImageAndGetUrl(BUNCHEOL_ID, file)).willReturn(url);
  }

  private void givenUploadFails(final ImageFile file) {
    given(imageUploader.uploadBuncheolImageAndGetUrl(BUNCHEOL_ID, file))
        .willThrow(new RuntimeException("S3 down"));
  }

  @Nested
  @DisplayName("대표사진 인덱스 보정 테스트")
  class ThumbnailPositionTest {

    @Test
    void 전부_업로드에_성공하면_지정_인덱스를_그대로_전달한다() {
      // given
      ImageFile file1 = imageFile("image1.jpg");
      ImageFile file2 = imageFile("image2.jpg");
      ImageFile file3 = imageFile("image3.jpg");
      givenUploadSucceeds(file1, "https://cdn.example.com/1.jpg");
      givenUploadSucceeds(file2, "https://cdn.example.com/2.jpg");
      givenUploadSucceeds(file3, "https://cdn.example.com/3.jpg");

      // when
      listener.handleImageUpload(
          new BuncheolImageUploadEvent(BUNCHEOL_ID, List.of(file1, file2, file3), 2));

      // then
      then(buncheolImageDomainService)
          .should()
          .createBuncheolImages(
              BUNCHEOL_ID,
              List.of(
                  "https://cdn.example.com/1.jpg",
                  "https://cdn.example.com/2.jpg",
                  "https://cdn.example.com/3.jpg"),
              2);
    }

    @Test
    void 지정_인덱스보다_앞의_업로드가_실패하면_성공분_기준_위치로_당겨_보정한다() {
      // given — 3장 중 첫 장 실패, 대표는 마지막 장(인덱스 2) → 성공분 [2.jpg, 3.jpg] 기준 위치 1.
      ImageFile file1 = imageFile("image1.jpg");
      ImageFile file2 = imageFile("image2.jpg");
      ImageFile file3 = imageFile("image3.jpg");
      givenUploadFails(file1);
      givenUploadSucceeds(file2, "https://cdn.example.com/2.jpg");
      givenUploadSucceeds(file3, "https://cdn.example.com/3.jpg");

      // when
      listener.handleImageUpload(
          new BuncheolImageUploadEvent(BUNCHEOL_ID, List.of(file1, file2, file3), 2));

      // then
      then(buncheolImageDomainService)
          .should()
          .createBuncheolImages(
              BUNCHEOL_ID,
              List.of("https://cdn.example.com/2.jpg", "https://cdn.example.com/3.jpg"),
              1);
    }

    @Test
    void 지정_인덱스의_업로드가_실패하면_대표사진_위치를_null로_전달한다() {
      // given — 대표로 지정한 장이 실패하면 플래그를 남기지 않는다(조회 쿼리가 MIN(id) 폴백).
      ImageFile file1 = imageFile("image1.jpg");
      ImageFile file2 = imageFile("image2.jpg");
      givenUploadSucceeds(file1, "https://cdn.example.com/1.jpg");
      givenUploadFails(file2);

      // when
      listener.handleImageUpload(
          new BuncheolImageUploadEvent(BUNCHEOL_ID, List.of(file1, file2), 1));

      // then
      then(buncheolImageDomainService)
          .should()
          .createBuncheolImages(BUNCHEOL_ID, List.of("https://cdn.example.com/1.jpg"), null);
    }

    @Test
    void thumbnailIndex가_null이면_대표사진_위치도_null로_전달한다() {
      // given — 수정 시 기존 이미지를 대표로 유지하는 경우 이번 업로드분엔 플래그를 남기지 않는다.
      ImageFile file1 = imageFile("image1.jpg");
      ImageFile file2 = imageFile("image2.jpg");
      givenUploadSucceeds(file1, "https://cdn.example.com/1.jpg");
      givenUploadSucceeds(file2, "https://cdn.example.com/2.jpg");

      // when
      listener.handleImageUpload(
          new BuncheolImageUploadEvent(BUNCHEOL_ID, List.of(file1, file2), null));

      // then
      then(buncheolImageDomainService)
          .should()
          .createBuncheolImages(
              BUNCHEOL_ID,
              List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"),
              null);
    }
  }

  @Nested
  @DisplayName("업로드 실패 처리 테스트")
  class UploadFailureTest {

    @Test
    void 전부_업로드에_실패하면_이미지를_저장하지_않는다() {
      // given
      ImageFile file1 = imageFile("image1.jpg");
      ImageFile file2 = imageFile("image2.jpg");
      givenUploadFails(file1);
      givenUploadFails(file2);

      // when
      listener.handleImageUpload(
          new BuncheolImageUploadEvent(BUNCHEOL_ID, List.of(file1, file2), 0));

      // then
      then(buncheolImageDomainService)
          .should(never())
          .createBuncheolImages(any(), anyList(), any());
    }
  }
}
