package com.rewardflow.app.controller.advice;

import com.rewardflow.api.dto.ApiResponse;
import com.rewardflow.app.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

  // 业务异常
  @ExceptionHandler(BizException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleBiz(BizException ex) {
    return ApiResponse.error(ex.getCode(), ex.getMessage());
  }

  // 参数校验异常
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
    String msg = ex.getBindingResult().getFieldErrors().stream()
        .map(e -> e.getField() + ":" + e.getDefaultMessage())
        .collect(Collectors.joining(", "));
    return ApiResponse.error(4000, msg);
  }

  // 参数校验异常 - 约束校验失败
  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleConstraint(ConstraintViolationException ex) {
    return ApiResponse.error(4000, ex.getMessage());
  }

  /**
    * 唯一键冲突异常，直接返回成功，幂等处理
    * 幂等兜底保护
   */
  @ExceptionHandler(DuplicateKeyException.class)
  @ResponseStatus(HttpStatus.OK)
  public ApiResponse<Void> handleDuplicate(DuplicateKeyException ex) {
    return ApiResponse.ok(null);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleOther(Exception ex) {
    return ApiResponse.error(5000, "internal error: " + ex.getClass().getSimpleName());
  }
}
