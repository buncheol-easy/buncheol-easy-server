package buncheoleasy.group.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Group {

  private Long id;
  private final String name;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
