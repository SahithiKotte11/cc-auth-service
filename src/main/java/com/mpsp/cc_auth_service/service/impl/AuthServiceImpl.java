package com.mpsp.cc_auth_service.service.impl;

import com.mpsp.cc_auth_service.dto.LoginRequest;
import com.mpsp.cc_auth_service.dto.LoginResponse;
import com.mpsp.cc_auth_service.dto.ResetPasswordRequest;
import com.mpsp.cc_auth_service.dto.User;
import com.mpsp.cc_auth_service.entity.LoginHistory;
import com.mpsp.cc_auth_service.entity.PasswordHistory;
import com.mpsp.cc_auth_service.entity.RefreshToken;
import com.mpsp.cc_auth_service.feignclients.UserServiceClient;
import com.mpsp.cc_auth_service.repository.LoginHistoryRepo;
import com.mpsp.cc_auth_service.repository.PasswordHistoryRepo;
import com.mpsp.cc_auth_service.repository.RefreshTokenRepo;
import com.mpsp.cc_auth_service.service.AuthService;
import com.mpsp.cc_auth_service.service.AwsService;
import com.mpsp.cc_auth_service.service.OtpService;
import com.mpsp.cc_auth_service.utils.GlobalExceptionHandler;
import com.mpsp.cc_auth_service.utils.JwtTokenProvider;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

  @Autowired
  private transient UserServiceClient userService;

  @Autowired
  private transient PasswordEncoder passwordEncoder;

  @Autowired
  private transient JwtTokenProvider jwtTokenProvider;

  @Autowired
  private transient LoginHistoryRepo loginHistoryRepository;

  @Autowired
  private transient PasswordHistoryRepo passwordHistoryRepository;

  @Autowired
  private transient RefreshTokenRepo refreshTokenRepository;

  @Autowired
  private transient OtpService otpService;

  @Autowired
  private transient AwsService awsService;

  @Value("${aws.ses.sender}")
  private String senderEmail;

  @Override
  @Transactional
  public LoginResponse login(final LoginRequest loginRequest) {
    final String email = loginRequest.getEmail();
    final String password = loginRequest.getPassword();

    // Validate user and password
    final User user = userService.findByEmail(email);
    user.setUserRole(User.UserRole.PRINCIPAL);

    log.info("User found: {}", user);

    final PasswordHistory pw = passwordHistoryRepository
        .findAllByUserId(user.getUserId(), PageRequest.of(0, 1, Sort.by("logoutTime").descending())).getContent().get(0);

    if (!passwordEncoder.matches(password, pw.getCurrentPassword())) {
      throw new BadCredentialsException("Invalid password");
    }

    // Generate tokens
    final String jwtToken = jwtTokenProvider.generateToken(user, false);
    final String refreshToken = jwtTokenProvider.generateToken(user, true);
    saveRefreshToken(user.getUserId(), refreshToken);

    loginHistoryRepository.save(new LoginHistory(user.getUserId(), LocalDateTime.now()));
    if (user.isFirstLogin()) {
      try {
        user.setFirstLogin(false);
        userService.updateUser(user.getUserId(), user);
      } catch (Exception e) {
        log.error("Error updating user", e);
      }
    }
    if (user.isMfaEnabled()) {
      otpService.sendOtp(email);
    }

    return new LoginResponse(jwtToken, refreshToken, user.isMfaEnabled(), user.isFirstLogin(), User.UserRole.PRINCIPAL);
  }

  @Override
  @Transactional
  public void logout(final String token) throws ParseException {
    final int userId = Integer.parseInt(jwtTokenProvider.getSubject(token));
    refreshTokenRepository.deleteRefreshToken(userId);

    final Page<LoginHistory> loginHistoryPage = loginHistoryRepository.findAllByUserId(userId,
        PageRequest.of(0, 1, Sort.by("lastLoginTime").descending()));
    if (!loginHistoryPage.isEmpty()) {
      final LoginHistory loginHistory = loginHistoryPage.getContent().get(0);
      loginHistory.setLogoutTime(LocalDateTime.now());
      loginHistoryRepository.saveAndFlush(loginHistory);
    }
  }

  @Transactional
  public LoginResponse refreshToken(final String refreshToken) {
    log.info("Refresh token: {}", refreshToken);
    RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
        .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

    jwtTokenProvider.verifyToken(refreshToken, storedToken.getUserId().toString(), true);

    // Generate new JWT token
    log.info("User ID: {}", storedToken.getUserId());
    final User user = userService.findById(storedToken.getUserId());

    final String newJwtToken = jwtTokenProvider.generateToken(user, false);
    final String newRefreshToken = jwtTokenProvider.generateToken(user, true);
    log.info("New refresh token: {}", newRefreshToken);
    updateRefreshToken(user.getUserId(), newRefreshToken);
    return new LoginResponse(newJwtToken, newRefreshToken, true, false, User.UserRole.PRINCIPAL);
  }

  @Override
  @Transactional(readOnly = true)
  public void sendResetPasswordEmail(String email) {
    userService.findByEmail(email);

    awsService.sendEmail(senderEmail, email, "cc_reset_password",
        Map.of("link", "http://platform-frontend-alb-946551445.ap-south-1.elb.amazonaws.com/user/change-password"));
  }

  @Override
  @Transactional
  public void resetPassword(final ResetPasswordRequest resetPasswordRequest, final String token) {
    final PasswordHistory passwordHistory;
    final int userId;
    try {
      log.info(jwtTokenProvider.getSubject(token));
      userId = Integer.parseInt(jwtTokenProvider.getSubject(token));
      log.info("User ID: {}", userId);
      jwtTokenProvider.verifyToken(token, String.valueOf(userId), false);
      passwordHistory = passwordHistoryRepository
          .findAllByUserId(userId, PageRequest.of(0, 1, Sort.by("logoutTime").descending())).getContent().get(0);
    } catch (ParseException e) {
      throw new GlobalExceptionHandler.RefreshTokenException("Invalid token");
    }
    if (passwordHistory != null) {
      passwordHistory.setCurrentPassword(passwordEncoder.encode(resetPasswordRequest.getPassword()));
      passwordHistory.setUserId(userId);
      passwordHistoryRepository.save(passwordHistory);
    }
  }

  @Transactional
  private void saveRefreshToken(final Integer userId, final String refreshToken) {
    final RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
        .orElse(new RefreshToken());

    token.setUserId(userId);
    token.setToken(refreshToken);
    token.setExpiresAt(LocalDateTime.now().plusDays(1));

    refreshTokenRepository.save(token);
  }

  @Transactional
  private void updateRefreshToken(Integer userId, String newRefreshToken) {
    refreshTokenRepository.updateRefreshToken(userId, newRefreshToken);
  }
}
