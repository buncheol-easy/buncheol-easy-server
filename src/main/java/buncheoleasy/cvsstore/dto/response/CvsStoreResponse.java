package buncheoleasy.cvsstore.dto.response;

import buncheoleasy.cvsstore.domain.CvsStore;
import java.math.BigDecimal;

/** 배송지 등록용 접수처 검색 결과 한 건. 좌표는 지도 마커 표시용 WGS84 경위도다. */
public record CvsStoreResponse(
    Long id,
    String brand,
    String name,
    String tel,
    String address,
    String postNo,
    BigDecimal latitude,
    BigDecimal longitude) {

  public static CvsStoreResponse from(final CvsStore store) {
    return new CvsStoreResponse(
        store.getId(),
        store.getBrand().name(),
        store.getName(),
        store.getTel(),
        store.getAddress(),
        store.getPostNo(),
        store.getLatitude(),
        store.getLongitude());
  }
}
