package buncheoleasy.cvsstore.domain;

import buncheoleasy.global.domain.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 편의점 택배 접수처(반값택배 취급점) 마스터.
 *
 * <p>크롤러(buncheoleasy-crawler)가 GS25(cvsnet)·CU(cupost) 조회 API 에서 수집·정규화해 S3 에 게시하고, 서버의
 * {@code CvsStoreSyncScheduler} 가 매주 diff 반영한다. 조회는 배송지 등록 화면의 지점 검색에 쓰인다.
 *
 * <ul>
 *   <li>{@code receiveYn}: 택배 접수(보내기) 가능 여부
 *   <li>{@code pickupYn}: 택배 픽업(수령) 가능 여부 — 배송지 후보는 이 값이 true 인 점포만이다
 * </ul>
 */
@Entity
@Table(name = "cvs_stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CvsStore extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "brand", nullable = false, length = 10)
  private CvsBrand brand;

  @Column(name = "store_code", nullable = false, length = 20)
  private String storeCode;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "tel", length = 20)
  private String tel;

  @Column(name = "sido", length = 20)
  private String sido;

  @Column(name = "address", nullable = false, length = 255)
  private String address;

  @Column(name = "post_no", length = 6)
  private String postNo;

  @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(name = "longitude", nullable = false, precision = 11, scale = 7)
  private BigDecimal longitude;

  @Column(name = "receive_yn", nullable = false)
  private boolean receiveYn;

  @Column(name = "pickup_yn", nullable = false)
  private boolean pickupYn;

  private CvsStore(
      final CvsBrand brand,
      final String storeCode,
      final String name,
      final String tel,
      final String sido,
      final String address,
      final String postNo,
      final BigDecimal latitude,
      final BigDecimal longitude,
      final boolean receiveYn,
      final boolean pickupYn) {
    this.brand = brand;
    this.storeCode = storeCode;
    this.name = name;
    this.tel = tel;
    this.sido = sido;
    this.address = address;
    this.postNo = postNo;
    this.latitude = latitude;
    this.longitude = longitude;
    this.receiveYn = receiveYn;
    this.pickupYn = pickupYn;
  }

  /**
   * 스냅샷 값으로 마스터 행을 갱신한다. 변경이 있었으면 true. 좌표는 스케일 차이(DB DECIMAL(·,7) vs
   * 스냅샷 원본)를 무시하도록 {@code compareTo} 로 비교한다.
   */
  public boolean applySnapshot(
      final String name,
      final String tel,
      final String sido,
      final String address,
      final String postNo,
      final BigDecimal latitude,
      final BigDecimal longitude,
      final boolean receiveYn,
      final boolean pickupYn) {
    final boolean changed =
        !this.name.equals(name)
            || !Objects.equals(this.tel, tel)
            || !Objects.equals(this.sido, sido)
            || !this.address.equals(address)
            || !Objects.equals(this.postNo, postNo)
            || this.latitude.compareTo(latitude) != 0
            || this.longitude.compareTo(longitude) != 0
            || this.receiveYn != receiveYn
            || this.pickupYn != pickupYn;
    if (!changed) {
      return false;
    }
    this.name = name;
    this.tel = tel;
    this.sido = sido;
    this.address = address;
    this.postNo = postNo;
    this.latitude = latitude;
    this.longitude = longitude;
    this.receiveYn = receiveYn;
    this.pickupYn = pickupYn;
    return true;
  }

  public static CvsStore create(
      final CvsBrand brand,
      final String storeCode,
      final String name,
      final String tel,
      final String sido,
      final String address,
      final String postNo,
      final BigDecimal latitude,
      final BigDecimal longitude,
      final boolean receiveYn,
      final boolean pickupYn) {
    return new CvsStore(
        brand, storeCode, name, tel, sido, address, postNo, latitude, longitude, receiveYn,
        pickupYn);
  }
}
