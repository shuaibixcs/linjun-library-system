package edu.linjun.library.controller;

import edu.linjun.library.common.*;
import edu.linjun.library.dto.LoginRequest;
import edu.linjun.library.service.AuthService;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @GetMapping("/health")
  public Object health() {
    return ApiResult.ok(Map.of("status", "UP"));
  }

  @PostMapping("/auth/login")
  public Object login(@Valid @RequestBody LoginRequest req) {
    return ApiResult.ok(auth.login(req));
  }

  @GetMapping("/auth/me")
  public Object me() {
    return ApiResult.ok(UserContext.user());
  }

  @PostMapping("/auth/logout")
  public Object logout(@RequestHeader("Authorization") String token) {
    auth.logout(token.substring(7));
    return ApiResult.ok(null);
  }

  @PostMapping("/auth/password")
  public Object password(@RequestBody Map<String, String> r) {
    auth.changePassword(r.get("oldPassword"), r.get("newPassword"));
    return ApiResult.ok(null);
  }
}
