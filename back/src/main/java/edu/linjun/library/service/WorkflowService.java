package edu.linjun.library.service;

import java.io.InputStream;
import java.util.Map;

public interface WorkflowService {
  Object perform(String action, Map<String, Object> values);

  Object dashboard();

  Object importStudents(InputStream file);

  void expireReservations();
}
