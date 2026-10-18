package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.io.IOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 분철 개최·수정 요청의 {@code images} 파트를 {@link ImageFile} 로 바꾼다. 형식 검증은 {@link ImageFile} 생성 시점이다. */
final class MultipartImageFiles {

  private MultipartImageFiles() {}

  static List<ImageFile> from(final List<MultipartFile> files) {
    if (files == null) {
      return List.of();
    }
    return files.stream().map(MultipartImageFiles::toImageFile).toList();
  }

  private static ImageFile toImageFile(final MultipartFile file) {
    try {
      return new ImageFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.FILE_READ_FAILED);
    }
  }
}
