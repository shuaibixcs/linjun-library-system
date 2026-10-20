package edu.linjun.library.controller;

import edu.linjun.library.common.*;
import edu.linjun.library.service.CatalogService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
  private final CatalogService service;

  public CatalogController(CatalogService s) {
    service = s;
  }

  @GetMapping("/options")
  public Object options() {
    return ApiResult.ok(service.options());
  }

  @GetMapping("/data/{module}")
  public Object list(@PathVariable String module, @RequestParam Map<String, String> p) {
    return ApiResult.ok(service.list(module, p));
  }

  @PostMapping("/data/{module}")
  public Object create(@PathVariable String module, @RequestBody Map<String, Object> v) {
    return ApiResult.ok(service.save(module, null, v));
  }

  @PutMapping("/data/{module}/{id}")
  public Object update(
      @PathVariable String module, @PathVariable long id, @RequestBody Map<String, Object> v) {
    return ApiResult.ok(service.save(module, id, v));
  }

  @DeleteMapping("/data/{module}/{id}")
  public Object delete(@PathVariable String module, @PathVariable long id) {
    service.delete(module, id);
    return ApiResult.ok(null);
  }
}
