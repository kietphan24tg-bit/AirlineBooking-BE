package com.example.moduleai.chat;

import org.springframework.stereotype.Component;

@Component
public class ChatFallbackCatalog {

  public String noContext() {
    return "I do not currently have enough information to confirm this. "
        + "Please contact support for an accurate check.";
  }

  public String retrievalUnavailable() {
    return "I cannot access policy information right now. "
        + "Please try again later or contact support.";
  }

  public String providerUnavailable() {
    return "The assistant service is temporarily unavailable. "
        + "Please try again later or contact support.";
  }

  public String emptyResponse() {
    return "I cannot provide a reliable answer right now. "
        + "Please try again or contact support.";
  }
}
