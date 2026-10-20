package edu.linjun.library.service;

import java.util.Map;

public interface CatalogService {
  Object list(String module, Map<String, String> params);

  Object save(String module, Long id, Map<String, Object> values);

  void delete(String module, long id);

  Object options();
}
