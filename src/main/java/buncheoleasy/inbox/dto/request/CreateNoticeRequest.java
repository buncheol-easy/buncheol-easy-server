package buncheoleasy.inbox.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 공지 작성 요청. 도메인에서도 동일하게 방어 검증한다. */
public record CreateNoticeRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 200) String reference,
    @NotBlank @Size(max = 5000) String description,
    boolean pinned,
    // in-app 상대 경로만 허용(`/`로 시작, 두 번째 문자로 `/`·`\` 금지). null 은 통과(연결 화면 없음).
    @Size(max = 500)
        @Pattern(
            regexp = "^/(?![/\\\\]).*",
            message = "연결 경로는 '//' 나 '/\\' 로 시작하지 않는 상대 경로(/...)여야 합니다.")
        String linkPath,
    // 홈 배너 정보(선택). 제공 시 배너 이미지 파트(bannerImage)와 함께 와야 한다(한쪽만 오면 INB-006).
    @Valid BannerCreateRequest banner) {}
