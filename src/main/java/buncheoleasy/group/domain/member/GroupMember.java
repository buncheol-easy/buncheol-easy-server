package buncheoleasy.group.domain.member;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GroupMember {

  private Long id;
  private final Long groupId;
  private final String name;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
