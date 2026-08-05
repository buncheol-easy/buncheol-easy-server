package buncheoleasy.cvsstore.domain;

/**
 * 접수처 검색 결과 한 건과 그 행이 나온 그룹 순위({@link CvsStoreCursor#RANK_NAME} /
 * {@link CvsStoreCursor#RANK_ADDRESS}). 그룹은 조회한 어댑터만이 확정할 수 있는 사실이므로, 커서 생성 시
 * 서비스가 키워드 매칭을 재계산하지 않고 이 값을 그대로 쓴다 (DB collation 과 Java 문자열 비교의 어긋남 방지).
 */
public record RankedCvsStore(int groupRank, CvsStore store) {}
