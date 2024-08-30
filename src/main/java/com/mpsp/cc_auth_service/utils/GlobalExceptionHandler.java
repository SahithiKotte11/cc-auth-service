package com.mpsp.cc_auth_service.utils;

import com.mpsp.cc_auth_service.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  // Handle Invalid Credentials Exception
  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(
      InvalidCredentialsException ex) {
    ErrorResponse errorResponse = new ErrorResponse("Invalid Credentials", ex.getMessage());
    return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
  }

  // Handle Refresh Token Exception
  @ExceptionHandler(RefreshTokenException.class)
  public ResponseEntity<ErrorResponse> handleRefreshTokenException(RefreshTokenException ex) {
    ErrorResponse errorResponse = new ErrorResponse("Invalid Token", ex.getMessage());
    return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
  }

  // Handle General Exception
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    ErrorResponse errorResponse = new ErrorResponse("Internal Server Error", ex.getMessage());
    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  public ResponseEntity<ErrorResponse> handleSesV2Exception(Exception e){
    ErrorResponse errorResponse = new ErrorResponse("Email not sent", e.getMessage());
    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  // Custom exception for invalid credentials
  public static class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
      super(message);
    }
  }

  // Custom exception for invalid refresh token
  public static class RefreshTokenException extends RuntimeException {
    public RefreshTokenException(String message) {
      super(message);
    }
  }

  public static class SesV2Exception extends RuntimeException {
    public SesV2Exception(String message) {super(message); }
  }


}
