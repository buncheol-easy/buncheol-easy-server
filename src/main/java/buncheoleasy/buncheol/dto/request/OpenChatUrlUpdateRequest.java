package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 오픈채팅 링크 전용 수정 요청. 전체 수정({@link BuncheolModifyRequest})과 달리 모집중이 끝난 뒤에도 열려 있다.
 *
 * @param openChatUrl 빈 문자열·공백 = 링크 제거, 값 = 형식 검증 후 교체. <b>필수 필드다</b> — 누락을 제거로 받으면 폼 조립 실수나
 *     "다른 필드만 보내는" 후속 호출이 링크를 조용히 지운다. 제거는 {@code ""} 로만 표현한다.
 */
public record OpenChatUrlUpdateRequest(@NotNull @Size(max = 200) String openChatUrl) {}
