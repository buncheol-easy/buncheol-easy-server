package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import buncheoleasy.buncheol.domain.Buncheol;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 최신 값 잠금 조회가 <b>잠금 읽기</b>로 다시 채우는지 고정한다.
 *
 * <p>H2 통합 테스트({@code JpaBuncheolRepositoryAdapterTest$FindLatestByIdForUpdateTest})는 같은 트랜잭션·같은 커넥션의 JDBC
 * UPDATE 를 쓰므로 일반 {@code refresh} 로 바꿔도 통과한다. 운영 MySQL(반복 읽기)에서는 일반 refresh 가 이 트랜잭션의 스냅샷(락
 * 이전)을 읽어, 락을 기다리는 사이 커밋된 조건 수정을 놓친다 — 그래서 잠금 모드를 넘기는 호출 자체를 여기서 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("최신 값 잠금 조회 — 잠금 읽기로 다시 채우기")
class JpaBuncheolRepositoryAdapterLatestLockTest {

  private static final Long BUNCHEOL_ID = 10L;

  @Mock private JpaBuncheolRepository jpaBuncheolRepository;
  @Mock private EntityManager entityManager;

  private JpaBuncheolRepositoryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new JpaBuncheolRepositoryAdapter(jpaBuncheolRepository);
    ReflectionTestUtils.setField(adapter, "entityManager", entityManager);
  }

  @Test
  void 잠금_조회_뒤_PESSIMISTIC_WRITE_로_다시_채운다() {
    Buncheol buncheol = mock(Buncheol.class);
    given(jpaBuncheolRepository.findByIdForUpdate(BUNCHEOL_ID)).willReturn(Optional.of(buncheol));

    Optional<Buncheol> latest = adapter.findLatestByIdForUpdate(BUNCHEOL_ID);

    assertThat(latest).containsSame(buncheol);
    InOrder order = inOrder(jpaBuncheolRepository, entityManager);
    order.verify(jpaBuncheolRepository).findByIdForUpdate(BUNCHEOL_ID);
    order.verify(entityManager).refresh(buncheol, LockModeType.PESSIMISTIC_WRITE);
    // 일반 refresh(잠금 모드 없음)를 함께 부르거나 그것으로 바꾸면 여기서 깨진다.
    verifyNoMoreInteractions(entityManager);
  }

  @Test
  void 분철이_없으면_다시_채우지_않는다() {
    given(jpaBuncheolRepository.findByIdForUpdate(BUNCHEOL_ID)).willReturn(Optional.empty());

    assertThat(adapter.findLatestByIdForUpdate(BUNCHEOL_ID)).isEmpty();
    verifyNoInteractions(entityManager);
  }
}
