package com.mpsp.cc_auth_service.dto;

import com.mpsp.cc_auth_service.utils.GeneratorUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

  @Schema(name = "email", example = "johndoe@gmail.com")
  @Email(message = "Invalid email")
  private String email;

  private String uniqueStudentId;

  private String role;

  @Schema(name = "password", example = "P@ssword123")
  @NotBlank(message = "Password is required")
  private String password;

  public String toString() {
    return String.format("LoginRequest{email='%s'}", GeneratorUtils.maskEmail(email));
  }
}
