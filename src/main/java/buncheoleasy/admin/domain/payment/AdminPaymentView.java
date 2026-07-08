package buncheoleasy.admin.domain.payment;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.User;

/**
 * 관리자 결제 목록의 행 1건 — 참여를 축으로 분철·그룹·참여자·멤버·배송을 단일 쿼리로 조인한 읽기 모델.
 *
 * <p>{@code participant} 는 탈퇴(soft delete)한 유저면 null, {@code member} 는 그룹 멤버 데이터가 지워졌으면 null,
 * {@code delivery} 는 배송 스냅샷 생성 전이면 null 이다.
 */
public record AdminPaymentView(
    Participation participation,
    Buncheol buncheol,
    Group group,
    User participant,
    GroupMember member,
    Delivery delivery) {}
