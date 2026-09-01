package com.example.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> badCredentials(BadCredentialsException e) {
        return Result.error(401, "用户名或密码错误");
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> disabled(DisabledException e) {
        return Result.error(401, "账号已停用");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> accessDenied(AccessDeniedException e) {
        return Result.error(403, "权限不足");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> other(Exception e) {
        return Result.error(500, e.getMessage() != null ? e.getMessage() : "服务器错误");
    }
}
