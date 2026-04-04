package com.example.moduleidentity.domain.security;

import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSecurityStateService {

  private final UserRepository userRepository;

  public UserSecurityStateService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional
  public OffsetDateTime invalidateTokens(UserEntity user) {
    OffsetDateTime invalidBefore = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    user.setTokenInvalidBefore(invalidBefore);
    user.setUpdatedAt(invalidBefore);
    userRepository.save(user);
    return invalidBefore;
  }

  public boolean isTokenIssuedBeforeInvalidation(UserEntity user, OffsetDateTime issuedAt) {
    OffsetDateTime tokenInvalidBefore = user.getTokenInvalidBefore();
    return tokenInvalidBefore != null && issuedAt != null && issuedAt.isBefore(tokenInvalidBefore);
  }
}
