package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 오픈채팅 링크 전용 수정 요청. 전체 수정({@link BuncheolModifyRequest})과 달리 모집중이 끝난 뒤에도 열려 있다.
 *
 * @param openChatUrl null·빈 문자열·공백 = 링크 제거, 값 = 형식 검증 후 교체. 전체 수정과 달리 null 을 "유지" 로 보지 않는다 — 이
 *     요청은 링크 하나만 보내므로 null 을 유지로 해석하면 "비우기" 를 표현할 방법이 없어진다.
 */
public record OpenChatUrlUpdateRequest(@Size(max = 200) String openChatUrl) {}
