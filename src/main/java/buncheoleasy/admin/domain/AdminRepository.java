package buncheoleasy.admin.domain;

import java.util.Optional;

public interface AdminRepository {

  /** 부트스트랩 시드 전용. 운영 중 계정 생성 API 는 제공하지 않는다. */
  Admin save(Admin admin);

  /** 관리자 본인 확인(/v1/admin/me)용. 계정이 삭제됐으면 empty. */
  Optional<Admin> findById(Long id);

  /** ID/PW 로그인용. */
  Optional<Admin> findByLoginId(String loginId);

  boolean existsByLoginId(String loginId);
}
