package edu.linjun.library.config;

import edu.linjun.library.common.*;
import edu.linjun.library.service.AuthService;
import javax.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthService auth;

  public WebConfig(AuthService auth) {
    this.auth = auth;
  }

  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              public boolean preHandle(
                  HttpServletRequest req, HttpServletResponse res, Object handler) {
                UserContext.clear();
                String h = req.getHeader("Authorization");
                if (h == null || !h.startsWith("Bearer "))
                  throw new BusinessException(
                      "请先登录", org.springframework.http.HttpStatus.UNAUTHORIZED);
                UserContext.set(auth.authenticate(h.substring(7)));
                return true;
              }

              public void afterCompletion(
                  HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
                UserContext.clear();
              }
            })
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/login", "/api/health");
  }
}
