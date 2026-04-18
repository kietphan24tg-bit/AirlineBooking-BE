package com.example.moduleflight.application;

import java.util.Optional;

public interface UserMarketProfileProvider {

  Optional<UserMarketProfile> findByUserId(Long userId);
}
