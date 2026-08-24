package com.example.moduleai.confirmation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ToolConfirmationMaintenanceJob {

  private final ToolConfirmationService toolConfirmationService;

  public ToolConfirmationMaintenanceJob(ToolConfirmationService toolConfirmationService) {
    this.toolConfirmationService = toolConfirmationService;
  }

  @Scheduled(fixedDelay = 60000)
  void maintain() {
    toolConfirmationService.expirePending();
    toolConfirmationService.reconcileUnknownExecutions();
  }
}
