package com.example.moduleai.conversation;

public record ChatOwner(Long userId, String guestToken, String guestTokenHash) {

  public boolean authenticated() {
    return userId != null;
  }

  public String ownerKey() {
    if (userId != null) {
      return "user:" + userId;
    }
    return "guest:" + guestTokenHash;
  }
}
