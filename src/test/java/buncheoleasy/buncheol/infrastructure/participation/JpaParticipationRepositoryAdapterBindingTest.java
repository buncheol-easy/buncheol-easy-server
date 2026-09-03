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

    assertSqlShape("status = 'RECRUITING'");
    assertBindingsInOrder("AWAITING_PAYMENT");
  }

  @Test
  void 추가모집_INSERT_도_같은_바인딩을_쓴다() throws Exception {
    JpaParticipationRepositoryAdapter adapter = adapterCapturingSql();

    adapter.saveIfCollecting(participation());

    assertSqlShape("status = 'PAYMENT_COLLECTING'");
    assertBindingsInOrder("AWAITING_PAYMENT");
  }

  private void assertSqlShape(final String buncheolCondition) {
    assertThat(capturedSql).doesNotContain("shipping_fee");
    assertThat(capturedSql).doesNotContain("shipping_address_id");
    // 🔴 컬럼 수 == 값 수. 어긋나면 MySQL 1136 으로 즉사한다 — 실제로 <b>양쪽을 세어</b> 비교한다.
    // (`?` 총개수만 보면 컬럼 목록에만 항목을 더하는 전형적 1136 오식이 그대로 통과한다.)
    assertThat(countColumns()).isEqualTo(countSelectExpressions());
    // 바인딩 개수와도 맞는다 — WHERE 의 ? 1개를 포함해 7.
    assertThat(capturedSql.chars().filter(c -> c == '?').count()).isEqualTo(7);
    // 두 SQL 이 실제로 갈리는지. 없으면 saveIfCollecting 이 모집중 SQL 을 써도 초록이다.
    assertThat(capturedSql).contains(buncheolCondition);
  }

  /** {@code INSERT INTO participations (...)} 괄호 안 항목 수. */
  private int countColumns() {
    int open = capturedSql.indexOf('(');
    int close = capturedSql.indexOf(')');
    return capturedSql.substring(open + 1, close).split(",").length;
  }

  /** {@code SELECT ... FROM} 사이 식 수. {@code flow_type}·{@code UTC_TIMESTAMP()} 2개를 포함한다. */
  private int countSelectExpressions() {
    int select = capturedSql.indexOf("SELECT ") + "SELECT ".length();
    int from = capturedSql.indexOf(" FROM ");
    return capturedSql.substring(select, from).split(",").length;
  }

  private void assertBindingsInOrder(final String status) throws Exception {
    InOrder order = inOrder(preparedStatement);
    order.verify(preparedStatement).setLong(1, BUNCHEOL_ID);
    order.verify(preparedStatement).setLong(2, BUNCHEOL_MEMBER_ID);
    order.verify(preparedStatement).setLong(3, PARTICIPANT_ID);
    order.verify(preparedStatement).setLong(4, AMOUNT);
    order.verify(preparedStatement).setTimestamp(eq(5), any(), any());
    order.verify(preparedStatement).setString(6, status);
    order.verify(preparedStatement).setLong(7, BUNCHEOL_ID); // WHERE id = ?

    // 🔴 배송비·배송지가 어느 칸으로도 새 나가지 않는다. 지울 줄을 잘못 골라 amount 를 지우고 당기면
    // 여기서 죽는다 — 셋 다 setLong 이라 드라이버 타입 검사로는 안 잡히는 오식이다.
    //
    // ⚠️ 배송지 제거는 배송비 때보다 위험하다. 배송비는 삭제 칸이 setLong 구간의 <b>마지막</b>이라
    // 뒤로 밀리는 게 전부 다른 타입(시각·문자)이었지만, 배송지(?4)는 뒤에 amount(long)가 따라온다.
    then(preparedStatement).should(never()).setLong(anyInt(), eq(SHIPPING_FEE));
    then(preparedStatement).should(never()).setLong(anyInt(), eq(SHIPPING_ADDRESS_ID));
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

  // 🔴 프로덕션 트래픽의 절반이 이 경로다(C2C 신청). dueAt 이 null 이라 setTimestamp 에 null 이 들어가는데,
  // 위 두 테스트는 AWAITING_PAYMENT 만 봐서 이 조합이 한 번도 실행되지 않았다.
  @Test
  void C2C_신청_INSERT_도_같은_인덱스에_기한과_상태를_넣는다() throws Exception {
    JpaParticipationRepositoryAdapter adapter = adapterCapturingSql();

    adapter.saveIfRecruiting(
        Participation.createApplied(
            BUNCHEOL_ID, BUNCHEOL_MEMBER_ID, PARTICIPANT_ID, SHIPPING_ADDRESS_ID, AMOUNT, SHIPPING_FEE));

    assertSqlShape("status = 'RECRUITING'");
    assertBindingsInOrder("APPLIED");
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
