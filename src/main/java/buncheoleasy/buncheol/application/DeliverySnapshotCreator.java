package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.domain.shipping.ShippingAddress;
import buncheoleasy.user.domain.shipping.ShippingAddressDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 개최자의 입금확인(AWAITING_PAYMENT → CONFIRMED) 시점에 배송 정보를 스냅샷으로 생성한다. 참여·배송지·유저 정보를 그 시점 값으로 박제해
 * {@link Delivery} 로 보관하므로 이후 원본 변경에 영향받지 않는다. 호출자 트랜잭션({@link
 * buncheoleasy.buncheol.application.participation.ParticipationService#confirmPayment}) 안에서 실행된다.
 *
 * <p>🔴 <b>단위는 참여가 아니라 묶음이다</b> (docs/70 결정 4 — 택배 1개 = 묶음 1개). 다슬롯 묶음은 슬롯마다
 * 입금확인이 돌지만 <b>택배는 하나</b>다. 슬롯마다 만들면 ① 개최자에게 같은 주소의 운송장 입력칸이 슬롯 수만큼
 * 뜨고 ② 참여자는 오지 않을 택배를 여러 건 기다리며 ③ P4 의 {@code uq_deliveries_bundle} 승격이 그 자리에서
 * 실패한다(실측: prod 묶음 64 · staging 66·83·87 이 그렇게 생겼다).
 *
 * <p>그래서 <b>그 묶음에 배송이 이미 있으면 만들지 않는다.</b> 이 판정은 호출자 트랜잭션 안의 조회라 같은 묶음의
 * 두 슬롯을 <b>동시에</b> 확인하면 둘 다 통과할 수 있다(check-then-insert 갭). 다만 묶음 입금확인은 묶음 전체를
 * 한 트랜잭션에서 처리하므로({@code ParticipationBundleService#confirmPayment}) 정상 경로에서 그 경합이
 * 생기지 않고, P4 의 유니크가 최종 차단한다.
 *
 * <p>{@code deliveries.participation_id} 는 UNIQUE 라 같은 참여에 두 번 호출하면 {@code
 * DataIntegrityViolationException} 이 난다. 참여당 입금확인은 1회뿐이라 정상 흐름에선 발생하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DeliverySnapshotCreator {

  private final DeliveryDomainService deliveryDomainService;
  private final ParticipationBundleDomainService participationBundleDomainService;
  private final ShippingAddressDomainService shippingAddressDomainService;
  private final UserDomainService userDomainService;

  public void create(final Participation participation) {
    // 이 묶음의 택배가 이미 있으면 두 번째 슬롯이다 — 만들지 않는다 (위 javadoc).
    // 묶음이 없는 참여(배포선 창에서 생긴 행)는 판정할 근거가 없으므로 종전대로 참여당 1건을 만든다.
    if (participation.getBundleId() != null
        && deliveryDomainService.findByBundleId(participation.getBundleId()).isPresent()) {
      return;
    }

    // 🔴 주소의 정본은 묶음이다 — 이 클래스는 이미 묶음 단위로 중복을 판정하는데(위 findByBundleId)
    // 주소만 참여 사본을 보고 있어 축이 어긋나 있었다. 사본은 P4 에서 사라진다.
    Long shippingAddressId = participationBundleDomainService.shippingAddressIdOf(participation);
    // 🔴 null 은 「참조 배송지가 삭제됐다」는 뜻이다(정본이 NULL 이면 사본으로 폴백하지 않는다 —
    // shippingAddressIdOf 참고). 그대로 넘기면 findById(null) 이 안내 문구 없는 500 으로 죽으므로
    // 명시적 ErrorCode 로 닫는다. 삭제 가드(existsActiveByShippingAddressId)가 정상 동작하면
    // 도달 불가에 가깝지만, 가드가 뚫렸을 때 <b>조용히</b>가 아니라 <b>이름을 가지고</b> 실패해야 한다.
    if (shippingAddressId == null) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);
    }
    ShippingAddress shippingAddress =
        shippingAddressDomainService.getShippingAddress(shippingAddressId);
    User user = userDomainService.getUser(participation.getParticipantId());
    // 참여 진입 가드 도입 전에 생성된 가입 미완료(Guest) 참여가 입금확인되면 아래 phoneNumber
    // 접근에서 NPE(500)가 난다. 명시적 403 으로 바꿔 레거시 데이터의 잔여 위험을 닫는다.
    // (profileCompleted 는 첫 전화번호 등록 시 true 로만 전이하므로 통과 = phoneNumber 존재 보장)
    user.requireProfileCompleted();

    Delivery delivery =
        Delivery.createSnapshot(
            // 묶음의 배송이 갖는 participation_id 는 그 묶음을 대표하는 슬롯(먼저 입금확인된 슬롯) 하나다.
            // 소유자 검증(DeliveryService)이 이 값을 쓰는데, 묶음은 한 사람의 것이라 어느 슬롯이든 같은 사람을
            // 가리킨다. P4 에서 이 칸이 사라지면 소유자 검증도 묶음으로 옮겨야 한다.
            participation.getId(),
            // 택배 1개 = 묶음 1개. 안 넣으면 참여는 묶음을 갖는데 배송만 미연결로 남아, P4 의
            // uq_deliveries_bundle 승격에서야 발견된다 (staging 에서 실제로 3건이 그렇게 샜다).
            participation.getBundleId(),
            shippingAddress.getShippingMethod(),
            shippingAddress.getStoreName(),
            user.getNickname().value(),
            user.getPhoneNumber().value());
    deliveryDomainService.createDelivery(delivery);
  }
}
