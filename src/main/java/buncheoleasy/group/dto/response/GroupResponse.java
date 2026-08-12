package buncheoleasy.group.dto.response;

import buncheoleasy.group.domain.Group;
import java.util.List;

/**
 * {@code aliases} 는 프론트의 클라이언트 사이드 랭킹({@code rankGroupSearchResults}) 이 소비한다. 서버가 별칭으로 매칭해 내려준 그룹을
 * 프론트가 이름만 보고 탈락시키지 않으려면 반드시 함께 내려야 한다.
 */
public record GroupResponse(Long id, String name, String image, List<String> aliases) {

  public static GroupResponse of(final Group group, final List<String> aliases) {
    return new GroupResponse(group.getId(), group.getName(), group.getImage(), aliases);
  }
}
