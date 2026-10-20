package edu.linjun.library.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.status).body(ApiResult.fail(e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(ApiResult.fail(e.getBindingResult().getAllErrors().get(0).getDefaultMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> duplicate(Exception e) {
    return ResponseEntity.badRequest().body(ApiResult.fail("数据重复或仍被其他业务引用，请检查编号和关联记录"));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<?> argument(Exception e) {
    return ResponseEntity.badRequest().body(ApiResult.fail("请求参数格式错误"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> other(Exception e) {
    log.error("Request failed", e);
    return ResponseEntity.status(500).body(ApiResult.fail("操作失败，请检查服务日志"));
  }
}
