package edu.linjun.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.linjun.library.common.*;
import edu.linjun.library.dto.LoginRequest;
import edu.linjun.library.entity.Account;
import edu.linjun.library.mapper.AccountMapper;
import edu.linjun.library.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
  private final AccountMapper accounts;
  private final Database db;
  public static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder();

  public AuthServiceImpl(AccountMapper accounts, Database db) {
    this.accounts = accounts;
    this.db = db;
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, Object> login(LoginRequest r) {
    var a = accounts.selectOne(new QueryWrapper<Account>().eq("username", r.username()));
    if (a == null || a.getEnabled() != 1 || !PASSWORDS.matches(r.password(), a.getPasswordHash()))
      throw new BusinessException("账号、密码错误或账号已停用", HttpStatus.UNAUTHORIZED);
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = HexFormat.of().formatHex(bytes);
    db.jdbc.update(
        "INSERT INTO auth_token(token_hash,user_id,expires_at) VALUES(?,?,?)",
        hash(token),
        a.getId(),
        LocalDateTime.now().plusHours(12));
    return Map.of("token", token, "user", publicAccount(a));
  }

  public Account authenticate(String token) {
    var rows =
        db.rows(
            "SELECT user_id FROM auth_token WHERE token_hash=? AND expires_at>?",
            hash(token),
            LocalDateTime.now());
    if (rows.isEmpty()) throw new BusinessException("登录已过期，请重新登录", HttpStatus.UNAUTHORIZED);
    var a = accounts.selectById(((Number) rows.get(0).get("userId")).longValue());
    if (a == null || a.getEnabled() != 1)
      throw new BusinessException("账号已停用", HttpStatus.UNAUTHORIZED);
    return publicAccount(a);
  }

  private Account publicAccount(Account source) {
    Account result = new Account();
    org.springframework.beans.BeanUtils.copyProperties(source, result);
    result.setPasswordHash(null);
    return result;
  }

  public void logout(String token) {
    db.jdbc.update("DELETE FROM auth_token WHERE token_hash=?", hash(token));
  }

  @Transactional
  public void changePassword(String old, String next) {
    if (next == null || next.length() < 8 || next.length() > 72)
      throw new BusinessException("新密码长度应为8到72位");
    var a = accounts.selectById(UserContext.user().getId());
    if (!PASSWORDS.matches(old, a.getPasswordHash())) throw new BusinessException("原密码错误");
    a.setPasswordHash(PASSWORDS.encode(next));
    accounts.updateById(a);
    db.jdbc.update("DELETE FROM auth_token WHERE user_id=?", a.getId());
  }
}
