package buncheoleasy.cvsstore.infrastructure;

import buncheoleasy.cvsstore.application.sync.CvsStoreSnapshot;
import buncheoleasy.cvsstore.application.sync.CvsStoreSnapshotReader;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Slf4j
@Component
public class S3CvsStoreSnapshotReader implements CvsStoreSnapshotReader {

  private final S3Client s3Client;
  private final ObjectMapper objectMapper;
  private final String bucketName;
  private final String snapshotKey;

  public S3CvsStoreSnapshotReader(
      final S3Client s3Client,
      final ObjectMapper objectMapper,
      @Value("${cloud.aws.s3.bucket}") final String bucketName,
      @Value("${app.cvs-store.sync.snapshot-key:cvs-stores/latest.json}")
          final String snapshotKey) {
    this.s3Client = s3Client;
    this.objectMapper = objectMapper;
    this.bucketName = bucketName;
    this.snapshotKey = snapshotKey;
  }

  @Override
  public Optional<CvsStoreSnapshot> loadLatest() {
    final GetObjectRequest request =
        GetObjectRequest.builder().bucket(bucketName).key(snapshotKey).build();
    try (InputStream body = s3Client.getObject(request)) {
      return Optional.of(objectMapper.readValue(body, CvsStoreSnapshot.class));
    } catch (final NoSuchKeyException ex) {
      // 크롤러가 아직 게시하지 않은 상태 — 오류가 아니라 "적용할 것 없음".
      log.info("접수처 스냅샷 미존재 — s3://{}/{}", bucketName, snapshotKey);
      return Optional.empty();
    } catch (final IOException | JacksonException ex) {
      throw new IllegalStateException("접수처 스냅샷 파싱 실패: " + snapshotKey, ex);
    }
  }
}
