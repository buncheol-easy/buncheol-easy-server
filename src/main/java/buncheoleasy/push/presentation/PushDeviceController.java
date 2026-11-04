package buncheoleasy.push.presentation;

import buncheoleasy.push.application.PushDeviceService;
import buncheoleasy.push.dto.request.RegisterPushDeviceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/push-devices")
@RequiredArgsConstructor
public class PushDeviceController {

  private final PushDeviceService pushDeviceService;

  /** 앱이 로그인·실행할 때 이 기기의 푸시 토큰을 등록한다. 같은 토큰이 이미 있으면 주인·환경을 지금 계정으로 바꾼다. */
  @PutMapping
  public ResponseEntity<Void> register(
      @AuthenticationPrincipal final Long userId,
      @Valid @RequestBody final RegisterPushDeviceRequest request) {
    pushDeviceService.register(userId, request);
    return ResponseEntity.noContent().build();
  }

  /** 로그아웃·알림 끄기. 본인 토큰만 지운다. 남의 토큰·없는 토큰도 204 — 존재 여부를 응답으로 흘리지 않는 멱등 삭제. */
  @DeleteMapping("/{token}")
  public ResponseEntity<Void> unregister(
      @AuthenticationPrincipal final Long userId, @PathVariable final String token) {
    pushDeviceService.unregister(userId, token);
    return ResponseEntity.noContent().build();
  }
}
