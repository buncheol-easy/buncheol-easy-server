package buncheoleasy.inbox.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 홈 배너 정보(선택). 제공 시 배너 이미지 파트(bannerImage)와 함께 와야 한다. */
public record BannerCreateRequest(@NotBlank @Size(max = 200) String title) {}
