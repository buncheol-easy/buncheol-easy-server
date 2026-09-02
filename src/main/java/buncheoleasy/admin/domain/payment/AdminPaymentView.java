package buncheoleasy.admin.domain.payment;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.shipping.ShippingAddress;

/**
 * 관리자 결제 목록의 행 1건 — 참여를 축으로 분철·그룹·참여자·멤버·배송을 단일 쿼리로 조인한 읽기 모델.
 *
 * <p>{@code participant} 는 탈퇴(soft delete)한 유저면 null, {@code member} 는 그룹 멤버 데이터가 지워졌으면 null,
 * {@code delivery} 는 배송 스냅샷 생성 전이거나, 스냅샷은 있어도 <b>그 슬롯이 아직 입금확인되지 않았으면</b>
 * null 이다 — 배송은 묶음에 붙어 있어 같은 묶음의 미입금 슬롯도 조인 키가 맞기 때문이다. {@code shippingAddress} 는 참여가 선택한 배송지의 현재 원본으로,
 * 배송지 미지정(레거시 행)이거나 원본이 삭제됐으면(종료된 참여 한정) null 이다.
 */
public record AdminPaymentView(
    Participation participation,
    Buncheol buncheol,
    Group group,
    User participant,
    GroupMember member,
    Delivery delivery,
    ShippingAddress shippingAddress) {}
