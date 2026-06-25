package buncheoleasy.notification.infrastructure;

import buncheoleasy.notification.domain.AlimtalkButton;
import java.util.List;

/**
 * 알림톡 단건 발송 요청. {@code message} 는 승인된 템플릿 본문과 개행까지 동일해야 하며(변수 치환 완료본), {@code buttons} 는 등록본과 동일한 순서·구성이어야
 * 한다(이미 링크 변수까지 치환된 상태). 버튼이 없으면 빈 리스트다.
 */
public record AlimtalkSendRequest(
    String tplCode,
    String receiverPhone,
    String subject,
    String message,
    List<AlimtalkButton> buttons) {}
