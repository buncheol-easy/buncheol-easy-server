package buncheoleasy.buncheol.application;

/**
 * 분철 검색 API 호출 성공 시 발행되는 이벤트. 비동기 listener 가 사용자의 최근 검색 이력을 갱신한다.
 *
 * <p>프론트는 사용자가 검색창에 친 텍스트를 (keyword|groupId|memberId) 셋으로 분기해 동시에 호출한다. 따라서 서버는 keyword 가 있는
 * 요청에서만 이벤트를 발행한다. {@code rawKeyword} 는 LIKE escape <b>전</b> trim 결과로, 사용자에게 다시 보여줄 원문이다.
 */
public record BuncheolSearchedEvent(Long userId, String rawKeyword) {}
