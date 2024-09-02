package com.mpsp.cc_auth_service.utils;

import com.mpsp.cc_auth_service.constants.AppConstants;
import com.mpsp.cc_auth_service.dto.User;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtTokenProvider {

  private final String issuer = "traitfit";
  private final JWSAlgorithm algorithm = JWSAlgorithm.HS256;

  @Value("${jwt.secret}")
  private String jwtSecret;

  @Value("${jwt.expiration}")
  private long jwtExpiration;

  @Value("${jwt.refresh.expiration}")
  private long refreshTokenExpiration;

  public String generateToken(final User user, final boolean isRefreshToken) {

    final JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(String.valueOf(user.getUserId()))
            .claim(AppConstants.IS_REFRESHTOKEN, isRefreshToken)
            .issueTime(new Date())
            .expirationTime(
                new Date(
                    System.currentTimeMillis()
                        + (isRefreshToken ? jwtExpiration : refreshTokenExpiration)))
            .issuer(issuer)
            .build();
    final Payload payload = new Payload(claims.toJSONObject());
    final JWSObject jwsObject = new JWSObject(new JWSHeader(algorithm), payload);
    try {
      jwsObject.sign(new MACSigner(jwtSecret));
    } catch (JOSEException e) {
      log.error("Failed to generate token", e);
      throw new RuntimeException(e.getMessage());
    }
    return jwsObject.serialize();
  }

  public boolean verifyToken(final String token, final String userId, final boolean isRefreshToken) {
    try {
      // Parse the token, stripping the "Bearer " prefix if present
      final JWSObject jwsObject = JWSObject.parse(token.startsWith("Bearer ") ? token.substring(7) : token);
      final JWSVerifier verifier = new MACVerifier(jwtSecret);

      // Create a claims verifier to match the expected claims
      final DefaultJWTClaimsVerifier<?> claimsVerifier =
              new DefaultJWTClaimsVerifier<>(
                      new JWTClaimsSet.Builder()
                              .issuer(issuer)
                              .subject(userId)
                              .claim(AppConstants.IS_REFRESHTOKEN, isRefreshToken)
                              .build(),
                      new HashSet<>(List.of("exp")));

      // Verify the token's signature
      if (jwsObject.verify(verifier)) {
        // Verify the token's claims
        try {
          claimsVerifier.verify(JWTClaimsSet.parse(jwsObject.getPayload().toJSONObject()), null);
          return true; // Token is valid
        } catch (BadJWTException e) {
          log.error("Token Verification failed: {}", e.getMessage());
          return false; // Invalid claims
        }
      } else {
        log.error("Invalid Token Signature");
        return false; // Invalid signature
      }
    } catch (JOSEException | ParseException e) {
      log.error("Failed to verify token: {}", e.getMessage());
      return false; // Parsing or verification exception
    }
  }


  public String getSubject(final String token) throws ParseException {

    final JWSObject jwsObject =
        JWSObject.parse(token.startsWith("Bearer ") ? token.substring(7) : token);

    final JWTClaimsSet claims = JWTClaimsSet.parse(jwsObject.getPayload().toJSONObject());
    return claims.getSubject();
  }

  public String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }

}
