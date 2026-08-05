package buncheoleasy.cvsstore.application.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("CvsStoreSnapshot 파싱 테스트")
class CvsStoreSnapshotParseTest {

  @Test
  void 크롤러가_게시하는_canonical_JSON_형식을_파싱한다() {
    // publish_cvs_snapshot.py 의 출력 형식 — 필드명·타입이 바뀌면 이 테스트가 먼저 깨져야 한다.
    String json =
        """
        {
          "generatedAt": "2026-08-04",
          "unknownFutureField": "ignored",
          "stores": [
            {
              "brand": "GS25",
              "storeCode": "VKK99",
              "name": "GS25청양타운점",
              "tel": "0415432724",
              "sido": "충남",
              "address": "충남 청양군 청양읍 칠갑산로 245 (읍내리 397-17)",
              "postNo": "33326",
              "latitude": 36.450938,
              "longitude": 126.803837,
              "receiveYn": true,
              "pickupYn": true
            }
          ]
        }
        """;

    CvsStoreSnapshot snapshot = new ObjectMapper().readValue(json, CvsStoreSnapshot.class);

    assertThat(snapshot.generatedAt()).isEqualTo("2026-08-04");
    assertThat(snapshot.stores()).hasSize(1);
    CvsStoreSnapshot.Row row = snapshot.stores().getFirst();
    assertThat(row.brand()).isEqualTo("GS25");
    assertThat(row.storeCode()).isEqualTo("VKK99");
    assertThat(row.latitude()).isEqualByComparingTo(new BigDecimal("36.450938"));
    assertThat(row.receiveYn()).isTrue();
  }
}
