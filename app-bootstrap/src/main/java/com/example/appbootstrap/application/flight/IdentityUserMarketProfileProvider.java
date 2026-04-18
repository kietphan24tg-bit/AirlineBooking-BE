package com.example.appbootstrap.application.flight;

import com.example.moduleflight.application.UserMarketProfile;
import com.example.moduleflight.application.UserMarketProfileProvider;
import com.example.moduleidentity.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class IdentityUserMarketProfileProvider implements UserMarketProfileProvider {

  private final UserRepository userRepository;

  public IdentityUserMarketProfileProvider(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public Optional<UserMarketProfile> findByUserId(Long userId) {
    if (userId == null) {
      return Optional.empty();
    }
    return userRepository
        .findById(userId)
        .map(user -> new UserMarketProfile(user.getNationality()));
  }
}
