package buncheoleasy.global.exception.handler;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  /*
  비즈니스 로직 수행 과정에서 발생하는 모든 예외 처리
   */
  @ExceptionHandler(BusinessException.class)
  public ProblemDetail handleBusinessException(final BusinessException exception) {
    return exception.getErrorCode().toProblemDetail();
  }

  /*
  @Valid 어노테이션을 사용한 요청 검증에 실패한 경우 발생하는 예외 처리
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handle(final MethodArgumentNotValidException exception) {
    return ErrorCode.INVALID_INPUT_VALUE.toProblemDetail();
  }

  /*
  @Validated 컨트롤러의 메서드 파라미터(@RequestParam 등) 검증 실패 처리
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handle(final ConstraintViolationException exception) {
    return ErrorCode.INVALID_INPUT_VALUE.toProblemDetail();
  }

  /*
  required = true 인 @RequestParam 키 자체가 누락된 요청 처리
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ProblemDetail handle(final MissingServletRequestParameterException exception) {
    return ErrorCode.INVALID_INPUT_VALUE.toProblemDetail();
  }
}
