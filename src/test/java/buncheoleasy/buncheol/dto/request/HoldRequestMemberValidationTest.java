package buncheoleasy.buncheol.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("개최 요청의 멤버 목록 검증")
class HoldRequestMemberValidationTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  @AfterAll
  static void closeFactory() {
    FACTORY.close();
  }

  private static Instant tomorrow() {
    return Instant.now().plus(Duration.ofDays(1));
  }

  private static HoldBuncheolRequest memberHold(final List<BuncheolMemberRequest> members) {
    return new HoldBuncheolRequest(
        1L, "제목", null, "구매처", tomorrow(), 1, 0, 0, null, null, 0, members);
  }

  private static HoldStaffBuncheolRequest staffHold(final List<BuncheolMemberRequest> members) {
    return new HoldStaffBuncheolRequest(
        1L, "제목", null, "구매처", tomorrow(), null, 1, 0, 0, null, 0, members);
  }

  private static List<String> paths(final Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream().map(v -> v.getPropertyPath().toString()).toList();
  }

  @Nested
  @DisplayName("회원 개최 요청")
  class MemberHold {

    @Test
    @DisplayName("멤버 가격이 음수면 그 멤버 칸에서 걸린다")
    void rejectsNegativePrice() {
      Set<ConstraintViolation<HoldBuncheolRequest>> violations =
          VALIDATOR.validate(
              memberHold(
                  List.of(
                      new BuncheolMemberRequest(1L, 1000L), new BuncheolMemberRequest(2L, -1L))));

      assertThat(paths(violations)).containsExactly("buncheolMembers[1].price");
    }

    @Test
    @DisplayName("멤버 ID가 비면 그 멤버 칸에서 걸린다")
    void rejectsMissingMemberId() {
      Set<ConstraintViolation<HoldBuncheolRequest>> violations =
          VALIDATOR.validate(memberHold(List.of(new BuncheolMemberRequest(null, 1000L))));

      assertThat(paths(violations)).containsExactly("buncheolMembers[0].memberId");
    }

    @Test
    @DisplayName("멤버 값이 모두 맞으면 통과한다")
    void acceptsValidMembers() {
      assertThat(VALIDATOR.validate(memberHold(List.of(new BuncheolMemberRequest(1L, 0L)))))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("운영진 개최 요청")
  class StaffHold {

    @Test
    @DisplayName("멤버 가격이 음수면 그 멤버 칸에서 걸린다")
    void rejectsNegativePrice() {
      Set<ConstraintViolation<HoldStaffBuncheolRequest>> violations =
          VALIDATOR.validate(staffHold(List.of(new BuncheolMemberRequest(1L, -1L))));

      assertThat(paths(violations)).containsExactly("buncheolMembers[0].price");
    }

    @Test
    @DisplayName("멤버 값이 모두 맞으면 통과한다")
    void acceptsValidMembers() {
      assertThat(VALIDATOR.validate(staffHold(List.of(new BuncheolMemberRequest(1L, 0L)))))
          .isEmpty();
    }
  }
}
