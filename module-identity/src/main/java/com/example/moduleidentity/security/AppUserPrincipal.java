package com.example.moduleidentity.security;

import com.example.moduleidentity.entity.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AppUserPrincipal implements UserDetails {

  private final Long userId;
  private final String email;
  private final String passwordHash;
  private final boolean enabled;
  private final Role role;
  private final List<GrantedAuthority> authorities;

  public AppUserPrincipal(
      Long userId, String email, String passwordHash, boolean enabled, Role role) {
    this.userId = userId;
    this.email = email;
    this.passwordHash = passwordHash;
    this.enabled = enabled;
    this.role = role;
    this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  public Long getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public Role getRole() {
    return role;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
