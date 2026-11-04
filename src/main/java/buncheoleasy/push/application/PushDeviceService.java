package buncheoleasy.push.application;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.push.domain.PushDevice;
import buncheoleasy.push.domain.PushDeviceRepository;
import buncheoleasy.push.dto.request.RegisterPushDeviceRequest;
import buncheoleasy.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 앱 푸시 기기 등록·해제. */
@Service
@RequiredArgsConstructor
public class PushDeviceService {

  /**
   * 회원 한 명의 기기 수 상한. 넘으면 가장 오래 등록된 기기부터 지운다. 사람 한 명의 기기로는 넉넉하고, 토큰을 마구 등록하는 계정이 알림 한 건마다 애플 호출을
   * 수없이 만들지 못하게 묶는다.
   */
  static final int MAX_DEVICES_PER_USER = 10;

  private final PushDeviceRepository pushDeviceRepository;
  private final UserRepository userRepository;

  /**
   * 토큰 기준 upsert — 같은 토큰이 있으면 지금 로그인한 계정의 것이 된다. 탈퇴한 회원은 액세스 토큰이 남아 있어도 등록하지 않는다(탈퇴 때 지운 기기가 되살아나지
   * 않게).
   */
  @Transactional
  public void register(final Long userId, final RegisterPushDeviceRequest request) {
    PushDevice device =
        PushDevice.create(userId, request.platform(), request.token(), request.environment());
    if (!userRepository.existsById(userId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    pushDeviceRepository.upsert(device);
    pushDeviceRepository.deleteAllByIdIn(
        pushDeviceRepository.findIdsToTrim(userId, MAX_DEVICES_PER_USER));
  }

  /** 본인 기기만 지운다. 남의 토큰·없는 토큰은 0행 — 멱등 성공. */
  @Transactional
  public void unregister(final Long userId, final String token) {
    pushDeviceRepository.deleteOwnedByToken(userId, PushDevice.normalizeToken(token));
  }

  /** 애플이 무효라고 답한 토큰. */
  @Transactional
  public void removeInvalidToken(final String token) {
    pushDeviceRepository.deleteByToken(token);
  }
}
