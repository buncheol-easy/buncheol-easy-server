package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.Group;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupMapper {

    boolean existsById(Long id);

    Optional<Group> findById(Long id);

    List<Group> selectByKeyword(@Param("keyword") String keyword);
}
