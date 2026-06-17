package buncheoleasy.inbox.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지 작성 요청. 도메인에서도 동일하게 방어 검증한다. */
public record CreateNoticeRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 200) String reference,
    @NotBlank @Size(max = 5000) String description,
    boolean pinned,
    @Size(max = 500) String linkPath) {}
