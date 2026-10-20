package edu.linjun.library.common;

import edu.linjun.library.entity.Account;
import org.springframework.http.HttpStatus;

public final class UserContext {
  private static final ThreadLocal<Account> CURRENT = new ThreadLocal<>();

  public static void set(Account a) {
    CURRENT.set(a);
  }

  public static void clear() {
    CURRENT.remove();
  }

  public static Account user() {
    Account a = CURRENT.get();
    if (a == null) throw new BusinessException("请先登录", HttpStatus.UNAUTHORIZED);
    return a;
  }

  public static boolean admin() {
    return !"STUDENT".equals(user().getRole());
  }

  public static void requireAdmin() {
    if (!admin()) throw new BusinessException("需要管理员权限", HttpStatus.FORBIDDEN);
  }

  public static void requireSuper() {
    if (!"SUPER_ADMIN".equals(user().getRole()))
      throw new BusinessException("需要超级管理员权限", HttpStatus.FORBIDDEN);
  }
}
