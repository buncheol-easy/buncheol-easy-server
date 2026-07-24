package buncheoleasy.admin.domain.payback;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.User;

/**
 * 관리자 배송비 환급 검수 목록의 행 1건 — 신청 이력이 있는 참여를 축으로 분철·신청자·멤버를 단일 쿼리로 조인한 읽기 모델.
 *
 * <p>{@code participant} 는 탈퇴(soft delete)한 유저면 null, {@code member} 는 그룹 멤버 데이터가 지워졌으면 null 이다.
 */
public record AdminPaybackView(
    Participation participation, Buncheol buncheol, User participant, GroupMember member) {}
