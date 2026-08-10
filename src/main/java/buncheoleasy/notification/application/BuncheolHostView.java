package buncheoleasy.notification.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.user.domain.User;

/** 개최자 대상 알림 변수 조립에 필요한 분철 단건 스냅샷. 참여 단건 없이 분철 단위로 발송할 때 쓴다. */
public record BuncheolHostView(Buncheol buncheol, User host) {}
