package edu.linjun.library.service;

import edu.linjun.library.dto.LoginRequest;
import edu.linjun.library.entity.Account;
import java.util.Map;

public interface AuthService {
  Map<String, Object> login(LoginRequest request);

  Account authenticate(String token);

  void logout(String token);

  void changePassword(String oldPassword, String newPassword);
}
