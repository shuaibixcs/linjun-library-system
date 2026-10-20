package edu.linjun.library.common;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class Database {
  public final JdbcTemplate jdbc;

  public Database(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.query(
        sql,
        (rs, n) -> {
          Map<String, Object> row = new LinkedHashMap<>();
          var meta = rs.getMetaData();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            String name = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
            StringBuilder camel = new StringBuilder();
            boolean upper = false;
            for (char c : name.toCharArray()) {
              if (c == '_') {
                upper = true;
                continue;
              }
              camel.append(upper ? Character.toUpperCase(c) : c);
              upper = false;
            }
            Object value = rs.getObject(i);
            if (value instanceof Timestamp t) value = t.toLocalDateTime();
            if (value instanceof java.sql.Date d) value = d.toLocalDate();
            row.put(camel.toString(), value);
          }
          return row;
        },
        args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var list = rows(sql, args);
    if (list.isEmpty()) throw new BusinessException("记录不存在");
    return list.get(0);
  }

  public long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql, Long.class, args);
    return n == null ? 0 : n;
  }

  public long insert(String table, Map<String, Object> fields) {
    String cols = String.join(",", fields.keySet());
    String marks = String.join(",", Collections.nCopies(fields.size(), "?"));
    var keys = new GeneratedKeyHolder();
    jdbc.update(
        conn -> {
          var ps =
              conn.prepareStatement(
                  "INSERT INTO " + table + " (" + cols + ") VALUES (" + marks + ")",
                  new String[] {"id"});
          int i = 1;
          for (var v : fields.values()) ps.setObject(i++, v);
          return ps;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  public static long id(Map<String, Object> m, String k) {
    try {
      return Long.parseLong(String.valueOf(m.get(k)));
    } catch (Exception e) {
      throw new BusinessException("缺少有效的 " + k);
    }
  }

  public static String text(Map<String, Object> m, String k) {
    var v = m.get(k);
    return v == null ? "" : v.toString().trim();
  }

  public static java.time.LocalDateTime time(Map<String, Object> m, String k) {
    try {
      return java.time.LocalDateTime.parse(text(m, k).replace(' ', 'T'));
    } catch (Exception e) {
      throw new BusinessException("时间格式错误：" + k);
    }
  }

  public static Map<String, Object> fields(Object... args) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < args.length; i += 2) m.put(args[i].toString(), args[i + 1]);
    return m;
  }
}
