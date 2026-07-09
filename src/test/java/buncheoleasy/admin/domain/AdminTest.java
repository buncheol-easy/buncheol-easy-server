package buncheoleasy.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Admin 테스트")
class AdminTest {

  private static final String ENCODED_PASSWORD = "{bcrypt}encoded-password-hash";

  @Nested
  @DisplayName("생성 테스트")
  class CreateTest {

    @Test
    void 로그인_ID와_인코딩된_비밀번호로_관리자를_생성한다() {
      Admin admin = Admin.create("buncheol-admin", ENCODED_PASSWORD);

      assertThat(admin.getLoginId()).isEqualTo("buncheol-admin");
      assertThat(admin.getPassword()).isEqualTo(ENCODED_PASSWORD);
    }

    @Test
    void 로그인_ID가_비어있으면_예외가_발생한다() {
      assertThatThrownBy(() -> Admin.create(" ", ENCODED_PASSWORD))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 로그인_ID가_50자를_넘으면_예외가_발생한다() {
      assertThatThrownBy(() -> Admin.create("a".repeat(51), ENCODED_PASSWORD))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 비밀번호가_비어있으면_예외가_발생한다() {
      assertThatThrownBy(() -> Admin.create("buncheol-admin", " "))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
  }
}
