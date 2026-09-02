package buncheoleasy.buncheol.infrastructure.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.participation.Participation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 참여 INSERT 의 <b>SQL 과 바인딩</b>을 DB 없이 고정한다.
 *
 * <p>🔴 <b>왜 이 테스트가 따로 필요한가.</b> 운영 INSERT 경로({@code saveIfRecruiting}/{@code saveIfCollecting})는
 * {@code UTC_TIMESTAMP()} 때문에 H2 에서 한 줄도 실행되지 않는다. 그래서 컬럼을 하나 빼며 파라미터 번호를 다시 매길 때
 * <b>한 칸 밀려도 컴파일되고 전체 스위트가 초록</b>이다 — {@code Participation} 의 "바인딩을 늘리면 인덱스가 밀려도
 * 잡아 줄 것이 없다" 는 주석이 그 상태를 말한다.
 *
 * <p>DB 는 없어도 된다. {@link PreparedStatementCreator} 를 캡처해 가짜 {@link PreparedStatement} 에 대고
 * 실행하면 세터 호출을 인덱스·타입·순서까지 단언할 수 있다. 이 테스트가 그 주석을 무효화한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("참여 INSERT 바인딩 테스트")
class JpaParticipationRepositoryAdapterBindingTest {

  private static final Long BUNCHEOL_ID = 7L;
  private static final Long BUNCHEOL_MEMBER_ID = 9L;
  private static final Long PARTICIPANT_ID = 11L;
  private static final Long SHIPPING_ADDRESS_ID = 13L;
  private static final long AMOUNT = 50_000L;
  // 🔴 이 값이 어느 칸으로도 새 나가면 안 된다. INSERT 는 더 이상 배송비를 싣지 않는다.
  private static final long SHIPPING_FEE = 3_000L;
  private static final Instant DUE_AT = Instant.parse("2026-09-02T12:30:00Z");

  @Mock private JpaParticipationRepository jpaParticipationRepository;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private PreparedStatement preparedStatement;

  private String capturedSql;

  @Test
  void 모집중_INSERT_는_배송비를_싣지_않고_바인딩이_밀리지_않는다() throws Exception {
    JpaParticipationRepositoryAdapter adapter = adapterCapturingSql();

    adapter.saveIfRecruiting(participation());

    assertSqlHasNoShippingFee();
    assertBindingsInOrder();
  }

  @Test
  void 추가모집_INSERT_도_같은_바인딩을_쓴다() throws Exception {
    JpaParticipationRepositoryAdapter adapter = adapterCapturingSql();

    adapter.saveIfCollecting(participation());

    assertSqlHasNoShippingFee();
    assertBindingsInOrder();
  }

  private void assertSqlHasNoShippingFee() {
    assertThat(capturedSql).doesNotContain("shipping_fee");
    // 컬럼 수와 값 수가 어긋나면 MySQL 1136 으로 즉사한다 — 여기서 먼저 잡는다.
    assertThat(capturedSql.chars().filter(c -> c == '?').count()).isEqualTo(8);
  }

  private void assertBindingsInOrder() throws Exception {
    InOrder order = inOrder(preparedStatement);
    order.verify(preparedStatement).setLong(1, BUNCHEOL_ID);
    order.verify(preparedStatement).setLong(2, BUNCHEOL_MEMBER_ID);
    order.verify(preparedStatement).setLong(3, PARTICIPANT_ID);
    order.verify(preparedStatement).setLong(4, SHIPPING_ADDRESS_ID);
    order.verify(preparedStatement).setLong(5, AMOUNT);
    order.verify(preparedStatement).setTimestamp(eq(6), any(), any());
    order.verify(preparedStatement).setString(7, "AWAITING_PAYMENT");
    order.verify(preparedStatement).setLong(8, BUNCHEOL_ID); // WHERE id = ?

    // 🔴 배송비가 어느 칸으로도 새 나가지 않는다. amount 줄을 잘못 지우고 당기면 여기서 죽는다 —
    // 둘 다 setLong 이라 드라이버 타입 검사로는 안 잡히는 유일한 오식이다.
    then(preparedStatement).should(never()).setLong(anyInt(), eq(SHIPPING_FEE));
  }

  private JpaParticipationRepositoryAdapter adapterCapturingSql() throws Exception {
    JpaParticipationRepositoryAdapter adapter =
        new JpaParticipationRepositoryAdapter(jpaParticipationRepository, jdbcTemplate);
    given(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
        .willAnswer(
            invocation -> {
              capturedSql = invocation.getArgument(0);
              return preparedStatement;
            });
    given(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
        .willAnswer(
            invocation -> {
              ((PreparedStatementCreator) invocation.getArgument(0))
                  .createPreparedStatement(connection);
              // 0 을 돌려주면 호출부가 "조건 불일치" 로 읽고 키 조회로 가지 않는다 — 바인딩만 보는 테스트다.
              return 0;
            });
    return adapter;
  }

  private Participation participation() {
    return Participation.create(
        BUNCHEOL_ID,
        BUNCHEOL_MEMBER_ID,
        PARTICIPANT_ID,
        SHIPPING_ADDRESS_ID,
        AMOUNT,
        SHIPPING_FEE,
        DUE_AT);
  }
}
