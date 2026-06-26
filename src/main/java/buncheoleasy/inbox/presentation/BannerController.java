package buncheoleasy.inbox.presentation;

import buncheoleasy.inbox.application.BannerQueryService;
import buncheoleasy.inbox.dto.response.BannerResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 홈 화면 배너 조회 (비로그인 허용). 배너가 등록된 공지를 개수 제한 없이 최신순으로 내려준다. */
@RestController
@RequestMapping("/v1/banners")
@RequiredArgsConstructor
public class BannerController {

  private final BannerQueryService bannerQueryService;

  @GetMapping
  public ResponseEntity<List<BannerResponse>> getBanners() {
    return ResponseEntity.ok(bannerQueryService.getBanners());
  }
}
