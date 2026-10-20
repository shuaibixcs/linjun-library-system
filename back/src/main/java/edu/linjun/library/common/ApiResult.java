package edu.linjun.library.common;

public record ApiResult<T>(boolean success, String message, T data) {
  public static <T> ApiResult<T> ok(T data) {
    return new ApiResult<>(true, "操作成功", data);
  }

  public static ApiResult<Void> fail(String message) {
    return new ApiResult<>(false, message, null);
  }
}
