package buncheoleasy.global.exception.handler;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.util.DisconnectedClientHelper;

@Slf4j
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

  /*
  required = true 인 @RequestPart(멀티파트 파트) 자체가 누락된 요청 처리 (예: 분철 개최 시 images 파트 누락)
   */
  @ExceptionHandler(MissingServletRequestPartException.class)
  public ProblemDetail handle(final MissingServletRequestPartException exception) {
    return ErrorCode.INVALID_INPUT_VALUE.toProblemDetail();
  }

  /*
  위 처리기에 걸리지 않은 예외. Spring·Security 가 원래 처리하던 예외는 다시 던져 지금 응답을 유지한다 —
  여기서 받으면 없는 주소(404)·타입 불일치(400)까지 500 + ERROR 알림이 된다. 진짜 고장만 C-002 + ERROR.
  쿼리스트링·본문은 토큰이 섞일 수 있어 로그에 남기지 않는다.
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(final Exception exception, final HttpServletRequest request)
      throws Exception {
    final Optional<ProblemDetail> causeResponse = findCauseResponse(exception);
    if (causeResponse.isPresent()) {
      return causeResponse.get();
    }
    if (isHandledByFramework(exception)) {
      throw exception;
    }
    log.error("처리되지 않은 예외 {} {}", request.getMethod(), request.getRequestURI(), exception);
    return ErrorCode.INTERNAL_SERVER_ERROR.toProblemDetail();
  }

  /*
  Exception 처리기가 생기면 Spring 이 원인 사슬로 처리기를 찾아가지 않는다(최상위에서 바로 매칭).
  감싸인 BusinessException·ConstraintViolationException 이 지금처럼 자기 응답을 내도록 직접 찾는다.
   */
  private Optional<ProblemDetail> findCauseResponse(final Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof BusinessException businessException) {
        return Optional.of(handleBusinessException(businessException));
      }
      if (current instanceof ConstraintViolationException constraintViolationException) {
        return Optional.of(handle(constraintViolationException));
      }
    }
    return Optional.empty();
  }

  /*
  DefaultHandlerExceptionResolver(4xx·503 등)·연결 끊김 처리·Security 필터(401/403)가 맡던 예외.
  500 인 ErrorResponse 와 ConversionNotSupportedException 은 Spring 도 500 으로 내던 것이라 여기서 받는다.
   */
  private boolean isHandledByFramework(final Exception exception) {
    if (exception instanceof ErrorResponse errorResponse) {
      return errorResponse.getStatusCode().value() != HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
    if (exception instanceof TypeMismatchException) {
      return !(exception instanceof ConversionNotSupportedException);
    }
    return exception instanceof HttpMessageNotReadableException
        || exception instanceof AsyncRequestNotUsableException
        || DisconnectedClientHelper.isClientDisconnectedException(exception)
        || exception instanceof AccessDeniedException
        || exception instanceof AuthenticationException;
  }
}
