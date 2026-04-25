package buncheoleasy.group.infrastructure;

import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.group.domain.member.GroupMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaGroupMemberRepositoryAdapter implements GroupMemberRepository {

  private final JpaGroupMemberRepository jpaGroupMemberRepository;

  @Override
  public List<GroupMember> findAllByGroupIdAndIds(Long groupId, List<Long> memberIds) {
    if (memberIds.isEmpty()) {
      return List.of();
    }
    return jpaGroupMemberRepository.findAllByGroupIdAndIds(groupId, memberIds);
  }

  @Override
  public List<GroupMember> findAllByGroupId(Long groupId) {
    return jpaGroupMemberRepository.findAllByGroupId(groupId);
  }
}
