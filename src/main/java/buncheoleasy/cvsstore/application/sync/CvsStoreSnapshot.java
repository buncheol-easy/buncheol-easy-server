package buncheoleasy.cvsstore.application.sync;

import java.math.BigDecimal;
import java.util.List;

/**
 * 크롤러가 S3 에 게시한 접수처 스냅샷 (buncheoleasy-crawler 의 publish_cvs_snapshot.py 산출물).
 * 정규화(브랜드 필터·가상점 제거·동일지점 중복 제거·시도 축약)는 크롤러가 끝낸 상태로 온다 — 서버는 적용만 한다.
 */
public record CvsStoreSnapshot(String generatedAt, List<Row> stores) {

  public record Row(
      String brand,
      String storeCode,
      String name,
      String tel,
      String sido,
      String address,
      String postNo,
      BigDecimal latitude,
      BigDecimal longitude,
      boolean receiveYn,
      boolean pickupYn) {}
}
