package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BuncheolMapper {

  void insert(Buncheol buncheol);

  Optional<Buncheol> findById(Long id);

  void update(@Param("id") Long id, @Param("params") BuncheolParams params);

  void updateStatus(@Param("id") Long id, @Param("status") BuncheolStatus status);
}
