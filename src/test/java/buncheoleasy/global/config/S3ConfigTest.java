package buncheoleasy.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.DigestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@DisplayName("S3Config 저장소 분기 테스트")
class S3ConfigTest {

  private static final byte[] BODY = {1, 2, 3, 4};

  private record CapturedRequest(String method, String path, Headers headers, byte[] body) {}

  private S3Client s3Client(final String region, final String endpoint) {
    S3Config config = new S3Config();
    setField(config, "accessKey", "test-access-key");
    setField(config, "secretKey", "test-secret-key");
    setField(config, "region", region);
    setField(config, "endpoint", endpoint);
    return config.s3Client();
  }

  private static String md5Hex(final byte[] bytes) {
    return DigestUtils.md5DigestAsHex(bytes);
  }

  private MockEnvironment yamlEnvironment() throws IOException {
    MockEnvironment environment = new MockEnvironment();
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yaml"))
        .forEach(environment.getPropertySources()::addLast);
    return environment;
  }

  @Test
  void 엔드포인트가_비어_있으면_AWS_S3_주소를_그대로_쓴다() {
    try (S3Client client = s3Client("ap-northeast-2", "")) {
      assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
      assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.AP_NORTHEAST_2);
      assertThat(client.utilities().getUrl(url -> url.bucket("bucket").key("key")).toString())
          .isEqualTo("https://bucket.s3.ap-northeast-2.amazonaws.com/key");
    }
  }

  @Test
  void 엔드포인트가_있으면_그_주소로_R2_가_받는_형태의_요청을_보낸다() throws IOException {
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          captured.set(
              new CapturedRequest(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestHeaders(),
                  exchange.getRequestBody().readAllBytes()));
          // SDK 가 응답 ETag 를 보낸 본문의 MD5 와 대조한다 — R2 도 단일 PUT 의 ETag 는 MD5 다.
          exchange.getResponseHeaders().add("ETag", "\"" + md5Hex(BODY) + "\"");
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();

    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    try (S3Client client = s3Client("auto", endpoint)) {
      client.putObject(
          PutObjectRequest.builder()
              .bucket("test-bucket")
              .key("buncheols/1/images/a.png")
              .contentType("image/png")
              .contentLength((long) BODY.length)
              .build(),
          RequestBody.fromBytes(BODY));
    } finally {
      server.stop(0);
    }

    CapturedRequest request = captured.get();
    assertThat(request.method()).isEqualTo("PUT");
    // 버킷이 호스트가 아니라 경로로 간다.
    assertThat(request.path()).isEqualTo("/test-bucket/buncheols/1/images/a.png");
    assertThat(request.headers().getFirst("Content-Type")).isEqualTo("image/png");
    // 청크 인코딩이 꺼져 있어야 R2 가 서명 불일치로 거절하지 않고, 본문도 원본 그대로 저장된다.
    assertThat(request.headers().getFirst("x-amz-content-sha256")).doesNotStartWith("STREAMING-");
    assertThat(request.headers().getFirst("Content-Encoding")).isNull();
    assertThat(request.body()).isEqualTo(BODY);
    // R2 가 지원하지 않는 ACL 이 없어야 한다. 체크섬 헤더는 SDK 2.30 부터 기본으로 붙어 호환 문제를 내므로,
    // SDK 를 올리면 이 검증이 깨진다 — 그때 requestChecksumCalculation(WHEN_REQUIRED) 를 함께 넣는다.
    assertThat(request.headers().keySet())
        .noneMatch(name -> name.toLowerCase().startsWith("x-amz-acl"))
        .noneMatch(name -> name.toLowerCase().startsWith("x-amz-checksum-"))
        .noneMatch(name -> name.equalsIgnoreCase("x-amz-sdk-checksum-algorithm"))
        .noneMatch(name -> name.equalsIgnoreCase("x-amz-trailer"));
  }

  @Test
  void 환경변수를_안_넣으면_저장소_설정이_예전_값과_같다() throws IOException {
    MockEnvironment environment = yamlEnvironment();

    assertThat(environment.getProperty("cloud.aws.region.static")).isEqualTo("ap-northeast-2");
    assertThat(environment.getProperty("cloud.aws.s3.endpoint")).isEmpty();
    assertThat(environment.getProperty("cloud.aws.s3.origin"))
        .isEqualTo("https://buncheol-easy-bucket.s3.ap-northeast-2.amazonaws.com");
  }

  @Test
  void 환경변수를_넣으면_R2_값으로_바뀐다() throws IOException {
    MockEnvironment environment =
        yamlEnvironment()
            .withProperty("AWS_REGION", "auto")
            .withProperty("S3_ENDPOINT", "https://account.r2.cloudflarestorage.com")
            .withProperty("S3_PUBLIC_ORIGIN", "https://images.buncheoleasy.com");

    assertThat(environment.getProperty("cloud.aws.region.static")).isEqualTo("auto");
    assertThat(environment.getProperty("cloud.aws.s3.endpoint"))
        .isEqualTo("https://account.r2.cloudflarestorage.com");
    assertThat(environment.getProperty("cloud.aws.s3.origin"))
        .isEqualTo("https://images.buncheoleasy.com");
  }
}
