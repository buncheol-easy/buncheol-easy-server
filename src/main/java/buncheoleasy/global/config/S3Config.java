package buncheoleasy.global.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class S3Config {

  @Value("${cloud.aws.credentials.access-key}")
  private String accessKey;

  @Value("${cloud.aws.credentials.secret-key}")
  private String secretKey;

  @Value("${cloud.aws.region.static}")
  private String region;

  // 비어 있으면 AWS S3 그대로 쓴다. S3 호환 저장소(Cloudflare R2)를 쓸 때만 채운다.
  @Value("${cloud.aws.s3.endpoint:}")
  private String endpoint;

  @Bean
  public S3Client s3Client() {
    AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials));
    if (StringUtils.hasText(endpoint)) {
      builder
          .endpointOverride(URI.create(endpoint))
          // Cloudflare 의 R2 Java 안내와 같은 설정이다 — 청크로 보내면 R2 가 서명 불일치(403)로 거절한다.
          .serviceConfiguration(
              S3Configuration.builder()
                  .pathStyleAccessEnabled(true)
                  .chunkedEncodingEnabled(false)
                  .build());
    }
    return builder.build();
  }
}
