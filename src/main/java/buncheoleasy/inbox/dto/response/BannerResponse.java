package buncheoleasy.inbox.dto.response;

import buncheoleasy.inbox.domain.InboxMessage;

/** 홈 배너 항목. 배너 제목·이미지와 연결된 공지 ID(상세 이동용)를 노출한다. */
public record BannerResponse(Long noticeId, String bannerTitle, String bannerImageUrl) {

  public static BannerResponse from(final InboxMessage message) {
    return new BannerResponse(
        message.getId(), message.getBannerTitle(), message.getBannerImageUrl());
  }
}
