package edu.linjun.library.service.impl;

import static edu.linjun.library.common.Database.*;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.linjun.library.common.*;
import edu.linjun.library.entity.*;
import edu.linjun.library.mapper.*;
import edu.linjun.library.service.CatalogService;
import edu.linjun.library.vo.PageResult;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogServiceImpl implements CatalogService {
  private final Database db;
  private final ObjectMapper json;
  private final Map<String, BaseMapper<?>> mappers;
  private final Map<String, Class<?>> entities;

  public CatalogServiceImpl(
      Database db,
      ObjectMapper json,
      AccountMapper a,
      CategoryMapper c,
      BookMapper b,
      ResourceMapper r,
      ActivityMapper act,
      AnnouncementMapper n) {
    this.db = db;
    this.json = json;
    this.mappers =
        Map.of(
            "students",
            a,
            "administrators",
            a,
            "categories",
            c,
            "books",
            b,
            "resources",
            r,
            "activities",
            act,
            "announcements",
            n);
    this.entities =
        Map.of(
            "students",
            Account.class,
            "administrators",
            Account.class,
            "categories",
            Category.class,
            "books",
            Book.class,
            "resources",
            Resource.class,
            "activities",
            Activity.class,
            "announcements",
            Announcement.class);
  }

  private String query(String m) {
    return switch (m) {
      case "students" ->
          "SELECT"
              + " a.id,a.username,a.name,a.role,a.enabled,a.student_no,a.department,a.phone,a.created_at,(SELECT"
              + " COUNT(*) FROM blacklist bl WHERE bl.user_id=a.id AND bl.active=1) AS blacklisted"
              + " FROM account a WHERE a.role='STUDENT'";
      case "administrators" ->
          "SELECT a.id,a.username,a.name,a.role,a.enabled,a.phone,a.created_at FROM account a WHERE"
              + " a.role<>'STUDENT'";
      case "categories" -> "SELECT c.* FROM category c WHERE 1=1";
      case "books" ->
          "SELECT b.*,c.name AS category_name,(SELECT COUNT(*) FROM book_copy x WHERE"
              + " x.book_id=b.id) AS total_stock,(SELECT COUNT(*) FROM book_copy x WHERE"
              + " x.book_id=b.id AND x.status='AVAILABLE') AS available_stock FROM book b LEFT JOIN"
              + " category c ON b.category_id=c.id WHERE 1=1";
      case "copies" ->
          "SELECT x.*,b.title FROM book_copy x JOIN book b ON b.id=x.book_id WHERE 1=1";
      case "acquisitions" ->
          "SELECT x.*,b.title,a.name AS operator_name FROM acquisition x JOIN book b ON"
              + " b.id=x.book_id JOIN account a ON a.id=x.operator_id WHERE 1=1";
      case "loans" ->
          "SELECT l.*,a.name AS student_name,a.student_no,b.title,x.code AS copy_code,CASE WHEN"
              + " l.status='BORROWED' AND l.due_at<CURRENT_TIMESTAMP THEN 'OVERDUE' ELSE l.status"
              + " END AS display_status FROM loan l JOIN account a ON a.id=l.user_id JOIN book_copy"
              + " x ON x.id=l.copy_id JOIN book b ON b.id=x.book_id WHERE 1=1";
      case "fines" ->
          "SELECT f.*,l.user_id,a.name AS student_name,a.student_no,b.title FROM fine f JOIN loan l"
              + " ON l.id=f.loan_id JOIN account a ON a.id=l.user_id JOIN book_copy x ON"
              + " x.id=l.copy_id JOIN book b ON b.id=x.book_id WHERE 1=1";
      case "blacklist" ->
          "SELECT bl.*,a.name AS student_name,a.student_no FROM blacklist bl JOIN account a ON"
              + " a.id=bl.user_id WHERE 1=1";
      case "resources" ->
          "SELECT r.*,(SELECT COUNT(*) FROM reservation v WHERE v.resource_id=r.id AND v.status IN"
              + " ('APPROVED','CHECKED_IN') AND v.start_at<=CURRENT_TIMESTAMP AND"
              + " v.end_at>CURRENT_TIMESTAMP)  + (SELECT COUNT(*) FROM activity a WHERE"
              + " a.resource_id=r.id AND a.status<>'CANCELLED' AND a.start_at<=CURRENT_TIMESTAMP"
              + " AND a.end_at>CURRENT_TIMESTAMP) AS occupied,(SELECT COUNT(*) FROM activity a"
              + " WHERE a.resource_id=r.id AND a.status<>'CANCELLED' AND"
              + " a.start_at<=CURRENT_TIMESTAMP AND a.end_at>CURRENT_TIMESTAMP) AS"
              + " activity_occupied FROM resource r WHERE 1=1";
      case "reservations" ->
          "SELECT v.*,a.name AS student_name,a.student_no,r.name AS resource_name,r.code AS"
              + " resource_code,r.kind,r.location FROM reservation v JOIN account a ON"
              + " a.id=v.user_id JOIN resource r ON r.id=v.resource_id WHERE 1=1";
      case "activities" ->
          "SELECT t.*,r.name AS resource_name,r.location,(SELECT COUNT(*) FROM registration g WHERE"
              + " g.activity_id=t.id AND g.status<>'CANCELLED') AS enrolled,(SELECT g.status FROM"
              + " registration g WHERE g.activity_id=t.id AND g.user_id="
              + UserContext.user().getId()
              + ") AS my_registration FROM activity t JOIN resource r ON r.id=t.resource_id WHERE"
              + " 1=1";
      case "registrations" ->
          "SELECT g.*,a.name AS student_name,a.student_no,t.title,t.start_at,t.end_at FROM"
              + " registration g JOIN account a ON a.id=g.user_id JOIN activity t ON"
              + " t.id=g.activity_id WHERE 1=1";
      case "announcements" ->
          "SELECT n.*,a.name AS author_name FROM announcement n JOIN account a ON a.id=n.author_id"
              + " WHERE 1=1";
      case "visits" ->
          "SELECT v.*,a.student_no,COALESCE(a.name,v.visitor_name) AS person_name FROM visit v LEFT"
              + " JOIN account a ON a.id=v.user_id WHERE 1=1";
      default -> throw new BusinessException("未知业务模块");
    };
  }

  public Object list(String m, Map<String, String> p) {
    if (Set.of("students", "administrators", "acquisitions", "blacklist", "visits").contains(m))
      UserContext.requireAdmin();
    if (m.equals("administrators")) UserContext.requireSuper();
    String sql = query(m);
    List<Object> args = new ArrayList<>();
    if (!UserContext.admin()) {
      String col =
          switch (m) {
            case "loans" -> "l.user_id";
            case "fines" -> "l.user_id";
            case "reservations" -> "v.user_id";
            case "registrations" -> "g.user_id";
            default -> null;
          };
      if (col != null) {
        sql += " AND " + col + "=?";
        args.add(UserContext.user().getId());
      }
      if (m.equals("books")) sql += " AND b.enabled=1";
    }
    String search =
        switch (m) {
          case "students", "administrators" ->
              "CONCAT(COALESCE(a.student_no,''),a.name,a.username)";
          case "categories" -> "c.name";
          case "books" -> "CONCAT(b.title,COALESCE(b.author,''),b.isbn)";
          case "copies", "acquisitions" -> "b.title";
          case "loans", "fines" -> "CONCAT(a.name,a.student_no,b.title)";
          case "blacklist" -> "CONCAT(a.name,a.student_no)";
          case "resources" -> "CONCAT(r.code,r.name,COALESCE(r.location,''))";
          case "reservations" -> "CONCAT(a.name,a.student_no,r.name)";
          case "activities", "registrations" -> "t.title";
          case "announcements" -> "n.title";
          case "visits" -> "COALESCE(a.name,v.visitor_name)";
          default -> "''";
        };
    if (!p.getOrDefault("q", "").isBlank()) {
      sql += " AND " + search + " LIKE ?";
      args.add("%" + p.get("q") + "%");
    }
    if (p.containsKey("kind")
        && !p.get("kind").isBlank()
        && Set.of("resources", "reservations").contains(m)) {
      sql += " AND r.kind=?";
      args.add(p.get("kind"));
    }
    if (p.containsKey("categoryId") && m.equals("books") && !p.get("categoryId").isBlank()) {
      sql += " AND b.category_id=?";
      args.add(Long.parseLong(p.get("categoryId")));
    }
    if (m.equals("copies") && p.containsKey("bookId")) {
      sql += " AND x.book_id=?";
      args.add(Long.parseLong(p.get("bookId")));
    }
    if (m.equals("registrations") && p.containsKey("activityId")) {
      sql += " AND g.activity_id=?";
      args.add(Long.parseLong(p.get("activityId")));
    }
    String alias =
        switch (m) {
          case "loans" -> "l";
          case "resources" -> "r";
          case "reservations", "visits" -> "v";
          case "activities" -> "t";
          case "registrations" -> "g";
          case "fines" -> "f";
          case "copies", "acquisitions" -> "x";
          default -> null;
        };
    if (p.containsKey("status")
        && !p.get("status").isBlank()
        && alias != null
        && !Set.of("visits", "acquisitions").contains(m)) {
      if (m.equals("loans") && p.get("status").equals("OVERDUE"))
        sql += " AND l.status='BORROWED' AND l.due_at<CURRENT_TIMESTAMP";
      else {
        sql += " AND " + alias + ".status=?";
        args.add(p.get("status"));
      }
    }
    if (m.equals("loans") && p.getOrDefault("due", "").equals("soon")) {
      sql += " AND l.status='BORROWED' AND l.due_at>=? AND l.due_at<=?";
      args.add(LocalDateTime.now());
      args.add(LocalDateTime.now().plusDays(3));
    }
    if (m.equals("visits") && !p.getOrDefault("date", "").isBlank()) {
      LocalDate d = LocalDate.parse(p.get("date"));
      sql += " AND v.entered_at>=? AND v.entered_at<?";
      args.add(d.atStartOfDay());
      args.add(d.plusDays(1).atStartOfDay());
    }
    int page = Math.max(1, Integer.parseInt(p.getOrDefault("page", "1")));
    int size = Math.min(100, Math.max(1, Integer.parseInt(p.getOrDefault("size", "10"))));
    long total = db.count("SELECT COUNT(*) FROM (" + sql + ") counted", args.toArray());
    String order =
        m.equals("announcements")
            ? " ORDER BY n.pinned DESC,n.id DESC"
            : " ORDER BY "
                + switch (m) {
                  case "students", "administrators" -> "a";
                  case "categories" -> "c";
                  case "books" -> "b";
                  case "copies", "acquisitions" -> "x";
                  case "loans" -> "l";
                  case "blacklist" -> "bl";
                  case "resources" -> "r";
                  case "reservations", "visits" -> "v";
                  case "activities", "registrations" -> m.equals("activities") ? "t" : "g";
                  case "fines" -> "f";
                  default -> "n";
                }
                + ".id DESC";
    args.add(size);
    args.add((page - 1) * size);
    return new PageResult<>(
        db.rows(sql + order + " LIMIT ? OFFSET ?", args.toArray()), total, page, size);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Object save(String m, Long recordId, Map<String, Object> values) {
    UserContext.requireAdmin();
    if (!mappers.containsKey(m)) throw new BusinessException("此模块使用专用业务操作");
    Map<String, Object> v = new LinkedHashMap<>();
    String allowed =
        switch (m) {
          case "students" -> "username,name,studentNo,department,phone,enabled";
          case "administrators" -> "username,name,phone,enabled,role";
          case "categories" -> "name,description";
          case "books" -> "isbn,title,author,publisher,categoryId,location,description,enabled";
          case "resources" -> "kind,code,name,location,capacity,status,equipmentStatus,description";
          case "activities" -> "title,description,resourceId,startAt,endAt,maxPeople,status";
          case "announcements" -> "title,content,pinned";
          default -> "";
        };
    for (String key : allowed.split(",")) if (values.containsKey(key)) v.put(key, values.get(key));
    if (recordId != null) {
      String table =
          switch (m) {
            case "students", "administrators" -> "account";
            case "categories" -> "category";
            case "books" -> "book";
            case "resources" -> "resource";
            case "activities" -> "activity";
            default -> "announcement";
          };
      db.one("SELECT id FROM " + table + " WHERE id=? FOR UPDATE", recordId);
      v.put("id", recordId);
    }
    if (m.equals("students") || m.equals("administrators")) {
      if (m.equals("administrators")) UserContext.requireSuper();
      if (recordId != null) {
        var old = db.one("SELECT role FROM account WHERE id=?", recordId);
        if (m.equals("students") && !old.get("role").equals("STUDENT")
            || m.equals("administrators") && old.get("role").equals("STUDENT"))
          throw new BusinessException("账号类型不匹配");
        if ("SUPER_ADMIN".equals(old.get("role"))) throw new BusinessException("超级管理员通过个人中心维护本人资料");
        if (recordId.equals(UserContext.user().getId())
            && String.valueOf(v.get("enabled")).equals("0"))
          throw new BusinessException("请保留当前登录账号启用状态");
      }
      v.put("role", m.equals("students") ? "STUDENT" : "ADMIN");
      if (recordId == null) {
        String password = text(values, "password");
        if (password.length() < 8 || password.length() > 72)
          throw new BusinessException("初始密码长度应为8到72位");
        v.put("passwordHash", AuthServiceImpl.PASSWORDS.encode(password));
        v.putIfAbsent("enabled", 1);
      }
      required(v, "username", "name");
      if (m.equals("students")) required(v, "studentNo");
      if (v.containsKey("enabled") && !Set.of("0", "1").contains(v.get("enabled").toString()))
        throw new BusinessException("账号状态错误");
    }
    if (m.equals("categories")) required(v, "name");
    if (m.equals("books")) {
      required(v, "isbn", "title");
      v.putIfAbsent("enabled", 1);
      if (v.containsKey("categoryId") && v.get("categoryId") != null)
        db.one("SELECT id FROM category WHERE id=?", id(v, "categoryId"));
    }
    if (m.equals("resources")) {
      required(v, "kind", "code", "name");
      if (!Set.of("LOCKER", "SEAT", "ROOM").contains(text(v, "kind")))
        throw new BusinessException("资源类型错误");
      v.putIfAbsent("capacity", 1);
      v.putIfAbsent("equipmentStatus", "NORMAL");
      if (!Set.of("NORMAL", "DAMAGED").contains(text(v, "equipmentStatus")))
        throw new BusinessException("设备状态错误");
      v.putIfAbsent("status", "OPEN");
      if (id(v, "capacity") < 1
          || !Set.of("OPEN", "CLOSED", "MAINTENANCE").contains(text(v, "status")))
        throw new BusinessException("资源容量或状态错误");
      if (recordId != null) {
        var old = db.one("SELECT kind FROM resource WHERE id=?", recordId);
        if (!old.get("kind").equals(v.get("kind"))) throw new BusinessException("资源类型建立后保持固定");
      }
    }
    if (m.equals("activities")) {
      required(v, "title", "resourceId", "startAt", "endAt", "maxPeople");
      long rid = id(v, "resourceId");
      var r = db.one("SELECT * FROM resource WHERE id=? FOR UPDATE", rid);
      if (!r.get("kind").equals("ROOM") || !r.get("status").equals("OPEN"))
        throw new BusinessException("请选择开放的房间");
      var start = time(v, "startAt");
      var end = time(v, "endAt");
      if (!end.isAfter(start) || recordId == null && !start.isAfter(LocalDateTime.now()))
        throw new BusinessException("活动时间范围错误");
      if (id(v, "maxPeople") < 1 || id(v, "maxPeople") > ((Number) r.get("capacity")).longValue())
        throw new BusinessException("人数上限应在房间容量范围内");
      v.putIfAbsent("status", "OPEN");
      if (!Set.of("OPEN", "CLOSED", "CANCELLED").contains(text(v, "status")))
        throw new BusinessException("活动状态错误");
      if (recordId != null
          && id(v, "maxPeople")
              < db.count(
                  "SELECT COUNT(*) FROM registration WHERE activity_id=? AND status<>'CANCELLED'",
                  recordId)) throw new BusinessException("人数上限小于已有报名人数");
      if (!text(v, "status").equals("CANCELLED")) checkRoom(db, rid, start, end, recordId, null);
      v.put("startAt", start);
      v.put("endAt", end);
    }
    if (m.equals("announcements")) {
      required(v, "title", "content");
      v.putIfAbsent("pinned", 0);
      if (recordId == null) v.put("authorId", UserContext.user().getId());
    }
    BaseMapper mapper = mappers.get(m);
    Object entity = json.convertValue(v, entities.get(m));
    if (recordId == null) mapper.insert(entity);
    else mapper.updateById(entity);
    if (entity instanceof Account a) a.setPasswordHash(null);
    return entity;
  }

  public static void required(Map<String, Object> v, String... keys) {
    for (String k : keys) if (text(v, k).isBlank()) throw new BusinessException("请填写 " + k);
  }

  public static void checkRoom(
      Database db,
      long rid,
      LocalDateTime start,
      LocalDateTime end,
      Long activityId,
      Long reservationId) {
    long n =
        db.count(
            "SELECT COUNT(*) FROM activity WHERE resource_id=? AND status<>'CANCELLED' AND"
                + " start_at<? AND end_at>? AND id<>?",
            rid,
            end,
            start,
            activityId == null ? -1 : activityId);
    n +=
        db.count(
            "SELECT COUNT(*) FROM reservation WHERE resource_id=? AND status IN"
                + " ('APPROVED','CHECKED_IN') AND start_at<? AND end_at>? AND id<>?",
            rid,
            end,
            start,
            reservationId == null ? -1 : reservationId);
    if (n > 0) throw new BusinessException("所选时段已被预约或活动占用");
  }

  @Transactional
  public void delete(String m, long id) {
    UserContext.requireAdmin();
    switch (m) {
      case "students", "administrators" -> {
        if (m.equals("administrators")) UserContext.requireSuper();
        var a = db.one("SELECT * FROM account WHERE id=? FOR UPDATE", id);
        if (a.get("role").equals("SUPER_ADMIN") || id == UserContext.user().getId())
          throw new BusinessException("请保留超级管理员和当前登录账号");
        if (m.equals("students") && !a.get("role").equals("STUDENT"))
          throw new BusinessException("账号类型错误");
        db.jdbc.update("UPDATE account SET enabled=0 WHERE id=?", id);
        db.jdbc.update("DELETE FROM auth_token WHERE user_id=?", id);
      }
      case "books" -> db.jdbc.update("UPDATE book SET enabled=0 WHERE id=?", id);
      case "categories" -> db.jdbc.update("DELETE FROM category WHERE id=?", id);
      case "resources" -> {
        db.one("SELECT id FROM resource WHERE id=? FOR UPDATE", id);
        if (db.count(
                    "SELECT COUNT(*) FROM reservation WHERE resource_id=? AND status IN"
                        + " ('PENDING','APPROVED','CHECKED_IN') AND end_at>?",
                    id,
                    LocalDateTime.now())
                > 0
            || db.count(
                    "SELECT COUNT(*) FROM activity WHERE resource_id=? AND status<>'CANCELLED' AND"
                        + " end_at>?",
                    id,
                    LocalDateTime.now())
                > 0) throw new BusinessException("资源仍有预约或活动，请先处理");
        db.jdbc.update("UPDATE resource SET status='CLOSED' WHERE id=?", id);
      }
      case "announcements" -> db.jdbc.update("DELETE FROM announcement WHERE id=?", id);
      case "activities" -> {
        db.one("SELECT id FROM activity WHERE id=? FOR UPDATE", id);
        db.jdbc.update("UPDATE activity SET status='CANCELLED' WHERE id=?", id);
      }
      default -> throw new BusinessException("此模块使用专用业务操作");
    }
  }

  public Object options() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("categories", db.rows("SELECT id,name FROM category ORDER BY name"));
    data.put("books", db.rows("SELECT id,title,isbn FROM book WHERE enabled=1 ORDER BY title"));
    data.put(
        "resources",
        db.rows(
            "SELECT id,kind,name,code,capacity,status,equipment_status FROM resource ORDER BY"
                + " kind,code"));
    if (UserContext.admin())
      data.put(
          "students",
          db.rows(
              "SELECT id,student_no,name,enabled FROM account WHERE role='STUDENT' ORDER BY"
                  + " student_no"));
    return data;
  }
}
