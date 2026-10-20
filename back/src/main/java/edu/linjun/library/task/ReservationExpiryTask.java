package edu.linjun.library.task;

import edu.linjun.library.service.WorkflowService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryTask {
  private final WorkflowService service;

  public ReservationExpiryTask(WorkflowService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void expire() {
    service.expireReservations();
  }
}
