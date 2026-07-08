package buncheoleasy.admin.infrastructure;

import buncheoleasy.admin.domain.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaAdminRepository extends JpaRepository<Admin, Long> {

  Optional<Admin> findByLoginId(String loginId);

  boolean existsByLoginId(String loginId);
}
