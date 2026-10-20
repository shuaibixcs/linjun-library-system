package edu.linjun.library.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  public final HttpStatus status;

  public BusinessException(String message) {
    this(message, HttpStatus.BAD_REQUEST);
  }

  public BusinessException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }
}
