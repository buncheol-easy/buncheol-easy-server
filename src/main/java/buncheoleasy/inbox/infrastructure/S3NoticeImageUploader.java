package buncheoleasy.inbox.infrastructure;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.inbox.application.image.ImageFile;
import buncheoleasy.inbox.application.image.NoticeImageUploader;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3NoticeImageUploader implements NoticeImageUploader {

  private static final String NOTICE_DIR = "notices/";
  private static final String IMAGE_DIR = "/images/";
  private static final String BANNER_DIR = "/banner/";
  private static final List<String> ALLOWED_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");

  private final S3Client s3Client;

  @Value("${cloud.aws.s3.bucket}")
  private String bucketName;

  @Value("${cloud.aws.s3.origin}")
  private String origin;

  @Override
  public String uploadNoticeImageAndGetUrl(final Long noticeId, final ImageFile imageFile) {
    return upload(noticeId, IMAGE_DIR, imageFile);
  }

  @Override
  public String uploadBannerImageAndGetUrl(final Long noticeId, final ImageFile imageFile) {
    return upload(noticeId, BANNER_DIR, imageFile);
  }

  private String upload(final Long noticeId, final String subDir, final ImageFile imageFile) {
    String key =
        NOTICE_DIR
            + noticeId
            + subDir
            + UUID.randomUUID()
            + extractExtension(imageFile.originalFilename());
    uploadObject(key, imageFile);

    return origin + "/" + key;
  }

  private String extractExtension(final String originalFilename) {
    if (originalFilename == null || !originalFilename.contains(".")) {
      throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
    }

    String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new BusinessException(ErrorCode.FILE_EXTENSION_INVALID);
    }

    return extension;
  }

  private void uploadObject(final String key, final ImageFile imageFile) {
    try {
      PutObjectRequest putObjectRequest =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(key)
              .contentType(imageFile.contentType())
              .contentLength((long) imageFile.bytes().length)
              .build();

      s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageFile.bytes()));
    } catch (AwsServiceException | SdkClientException e) {
      log.error("S3 이미지 업로드 실패. key: {}", key, e);
      throw new BusinessException(ErrorCode.S3_UPLOAD_FAILED);
    }
  }
}
