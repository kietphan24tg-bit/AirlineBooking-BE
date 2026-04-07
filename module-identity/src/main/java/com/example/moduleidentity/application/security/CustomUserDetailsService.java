package com.example.moduleidentity.application.security;

import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public CustomUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    UserEntity user =
        userRepository
            .findByEmail(email)
            .orElseThrow(
                () -> new UsernameNotFoundException("Invalid credentials"));

    return new AppUserPrincipal(
        user.getId(),
        user.getEmail(),
        user.getPasswordHash(),
        user.getRole(),
        user.getTokenInvalidBefore());
  }
}
