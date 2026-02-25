package buncheoleasy.group.domain;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {

    boolean existsById(Long id);

    Optional<Group> findById(Long id);

    List<Group> findByKeyword(String keyword);
}
