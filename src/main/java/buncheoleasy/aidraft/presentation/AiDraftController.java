package buncheoleasy.aidraft.presentation;

import buncheoleasy.aidraft.application.AiDraftService;
import buncheoleasy.aidraft.dto.request.CreateAiDraftRequest;
import buncheoleasy.aidraft.dto.response.AiDraftQuotaResponse;
import buncheoleasy.aidraft.dto.response.AiDraftResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 개최글 도우미. {@code /v1/buncheols/} 밑에 두지 않는다 — 그 아래 한 단계 GET 은 비로그인 공개라(SecurityConfig) 남은 횟수 조회가 새어 나간다.
 * 두 경로 모두 SecurityConfig 의 기본 규칙(유저 토큰 필수, 관리자 토큰 거절)을 받는다.
 */
@RestController
@RequestMapping("/v1/ai/drafts")
@RequiredArgsConstructor
public class AiDraftController {

  private final AiDraftService aiDraftService;

  @PostMapping
  public ResponseEntity<AiDraftResponse> createDraft(
      @AuthenticationPrincipal final Long userId, @RequestBody final CreateAiDraftRequest request) {
    return ResponseEntity.ok(aiDraftService.createDraft(userId, request));
  }

  @GetMapping("/quota")
  public ResponseEntity<AiDraftQuotaResponse> getQuota(@AuthenticationPrincipal final Long userId) {
    return ResponseEntity.ok(aiDraftService.getQuota(userId));
  }
}
