package com.mpsp.cc_auth_service.service.impl;

import com.mpsp.cc_auth_service.dto.User;
import com.mpsp.cc_auth_service.entity.OtpGen;
import com.mpsp.cc_auth_service.feignclients.UserServiceClient;
import com.mpsp.cc_auth_service.repository.OtpGenRepo;
import com.mpsp.cc_auth_service.service.AwsService;
import com.mpsp.cc_auth_service.service.OtpService;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.mpsp.cc_auth_service.utils.GeneratorUtils;
import com.mpsp.cc_auth_service.utils.GlobalExceptionHandler;
import com.mpsp.cc_auth_service.utils.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OtpServiceImpl implements OtpService {

  @Autowired private transient UserServiceClient userService;

  @Autowired private transient OtpGenRepo otpGenRepo;

  @Autowired private transient AwsService awsService;

  @Autowired private transient JwtTokenProvider jwtTokenProvider;

  @Override
  public String sendOtp(String email) {
    User user = userService.findByEmail(email);
    if (user == null) {
      throw new UsernameNotFoundException("User not found");
    }
    OtpGen otpGen = otpGenRepo.findByUserId(user.getUserId());
    String otp = GeneratorUtils.generateOTP(4);

    awsService.sendEmail("sahithi.k@traitfit.com", email, "login_cc_otp",  Map.of("otp",otp));
    if (otpGen == null) {
      otpGen = new OtpGen();
      otpGen.setUserId(user.getUserId());
      otpGen.setOtp(otp);
      otpGen.setCreatedAt(LocalDateTime.now());
      otpGen.setModifiedAt(LocalDateTime.now());
      otpGenRepo.saveAndFlush(otpGen);
    } else {
      otpGen.setModifiedAt(LocalDateTime.now());
      otpGen.setOtp(otp);
      otpGenRepo.saveAndFlush(otpGen);
    }
    return otp;
  }

  @Override
  public boolean verifyOtp(String token, String otp) {
    int userId;
    try {
      log.info("token: {}", token);
      userId = Integer.parseInt(jwtTokenProvider.getSubject(token));
    }catch (ParseException e){
      throw new GlobalExceptionHandler.RefreshTokenException("Invalid token");
    }
    if (userId == 0) {
      throw new UsernameNotFoundException("User not found");
    }
    OtpGen otpGen = otpGenRepo.findByUserId(userId);
    if (otpGen == null) {
      return false;
    }
    if (otpGen.getModifiedAt().isBefore(LocalDateTime.now().minusHours(1))) {
      throw new RuntimeException("OTP expired");
    }
    if(otpGen.getOtp().equals(otp)){
        return true;
    }
    return false;
  }

  @Override
  public void resendOtp(String email) {
    User user = userService.findByEmail(email);
    if (user == null) {
      throw new UsernameNotFoundException("User not found");
    }
    sendOtp(email);
  }

}
