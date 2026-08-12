package buncheoleasy.admin.application;

import buncheoleasy.admin.dto.response.AdminImpersonationTokenResponse;
import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.UserDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 대상 유저의 짧은 수명 access token(ROLE_USER)을 발급받아 문의를 재현하는 기능. 발급 토큰은 일반 유저 토큰과 구분되지 않는다(role
 * claim 없음) — 유저 API 를 그대로 호출해 화면을 충실히 재현하기 위함이다. 그만큼 쓰기 액션도 실제로 반영되므로 수명을 짧게(기본 15분) 두고 조회
 * 위주 사용을 전제한다. 개인정보 접근이므로 누가·언제·누구를·왜 발급했는지 감사 로그로 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminImpersonationService {

  private final UserDomainService userDomainService;
  private final JwtTokenProvider jwtTokenProvider;

  @Transactional(readOnly = true)
  public AdminImpersonationTokenResponse issueToken(
      final Long adminId, final Long targetUserId, final String reason) {
    if (!userDomainService.isValidUser(targetUserId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }

    final String accessToken = jwtTokenProvider.createImpersonationAccessToken(targetUserId);
    final long expiresInSeconds = jwtTokenProvider.getImpersonationTokenExpirationSeconds();

    // 감사 로그: 개인정보 접근이므로 반드시 남긴다. 누가(adminId) 누구를(targetUserId) 왜(reason) 접속했는지.
    log.warn(
        "관리자 impersonation 토큰 발급. adminId={}, targetUserId={}, reason={}, expiresInSeconds={}",
        adminId,
        targetUserId,
        reason,
        expiresInSeconds);

    return new AdminImpersonationTokenResponse(targetUserId, accessToken, expiresInSeconds);
  }
}
