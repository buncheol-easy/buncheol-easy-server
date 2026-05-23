package buncheoleasy.global.page;

import java.time.Instant;

/** 커서 페이지네이션의 정렬 기준이 되는 (createdAt, id) 쌍을 노출하는 도메인 마커. */
public interface Cursorable {

  Instant getCreatedAt();

  Long getId();
}
