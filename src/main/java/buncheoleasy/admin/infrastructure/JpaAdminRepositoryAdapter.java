package buncheoleasy.admin.infrastructure;

import buncheoleasy.admin.domain.Admin;
import buncheoleasy.admin.domain.AdminRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaAdminRepositoryAdapter implements AdminRepository {

  private final JpaAdminRepository jpaAdminRepository;

  @Override
  public Admin save(final Admin admin) {
    return jpaAdminRepository.save(admin);
  }

  @Override
  public Optional<Admin> findById(final Long id) {
    return jpaAdminRepository.findById(id);
  }

  @Override
  public Optional<Admin> findByLoginId(final String loginId) {
    return jpaAdminRepository.findByLoginId(loginId);
  }

  @Override
  public boolean existsByLoginId(final String loginId) {
    return jpaAdminRepository.existsByLoginId(loginId);
  }
}
