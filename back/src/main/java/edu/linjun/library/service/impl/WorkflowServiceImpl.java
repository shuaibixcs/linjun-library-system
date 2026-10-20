package edu.linjun.library.service.impl;

import static edu.linjun.library.common.Database.*;
import static edu.linjun.library.service.impl.CatalogServiceImpl.*;

import edu.linjun.library.common.*;
import edu.linjun.library.service.WorkflowService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowServiceImpl implements WorkflowService {
  private final Database db;

  @Value("${library.borrow-days:30}")
  private int borrowDays;

  @Value("${library.max-loans:5}")
  private int maxLoans;

  @Value("${library.fine-per-day:0.5}")
  private BigDecimal finePerDay;

  @Value("${library.locker-max-days:30}")
  private int lockerMaxDays;

  @Value("${library.checkin-grace-minutes:30}")
  private int grace;

  public WorkflowServiceImpl(Database db) {
    this.db = db;
  }

  private long me() {
    return UserContext.user().getId();
  }

  private void eligible(long uid) {
    var u = db.one("SELECT * FROM account WHERE id=? FOR UPDATE", uid);
    if (!u.get("role").equals("STUDENT") || ((Number) u.get("enabled")).intValue() != 1)
      throw new BusinessException("学生账号不存在或已停用");
    if (db.count("SELECT COUNT(*) FROM blacklist WHERE user_id=? AND active=1", uid) > 0)
      throw new BusinessException("该学生处于黑名单，暂时禁止借阅、预约和入馆");
  }

  private void owner(Map<String, Object> row) {
    if (!UserContext.admin() && id(row, "userId") != me())
      throw new BusinessException("仅可操作本人记录", org.springframework.http.HttpStatus.FORBIDDEN);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Object perform(String action, Map<String, Object> v) {
    return switch (action) {
      case "acquire" -> acquire(v);
      case "borrow" -> borrow(v);
      case "return" -> returnBook(v);
      case "blacklist-add" -> addBlacklist(v);
      case "blacklist-remove" -> removeBlacklist(v);
      case "reserve" -> reserve(v);
      case "review" -> review(v);
      case "reservation-cancel" -> cancelReservation(v);
      case "reservation-checkin" -> checkin(v);
      case "register" -> register(v);
      case "registration-cancel" -> cancelRegistration(v);
      case "activity-checkin" -> activityCheckin(v);
      case "visit" -> visit(v);
      case "fine-settle" -> settleFine(v);
      default -> throw new BusinessException("未知业务操作");
    };
  }

  private Object acquire(Map<String, Object> v) {
    UserContext.requireAdmin();
    long bid = id(v, "bookId");
    long qty = id(v, "quantity");
    if (qty < 1 || qty > 500) throw new BusinessException("单次入库数量应为1到500本");
    var book = db.one("SELECT * FROM book WHERE id=? FOR UPDATE", bid);
    if (((Number) book.get("enabled")).intValue() != 1) throw new BusinessException("图书已停用");
    String batch = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    for (int i = 1; i <= qty; i++)
      db.insert(
          "book_copy",
          fields(
              "book_id", bid, "code", "LJ-" + bid + "-" + batch + "-" + i, "status", "AVAILABLE"));
    return Map.of(
        "id",
        db.insert(
            "acquisition",
            fields(
                "book_id", bid, "quantity", qty, "source", text(v, "source"), "operator_id", me())),
        "quantity",
        qty);
  }

  private Object borrow(Map<String, Object> v) {
    UserContext.requireAdmin();
    long uid = id(v, "userId");
    eligible(uid);
    if (db.count("SELECT COUNT(*) FROM loan WHERE user_id=? AND status='BORROWED'", uid)
        >= maxLoans) throw new BusinessException("已达到最大借阅数量");
    required(v, "copyCode");
    var copy =
        db.one(
            "SELECT x.*,b.enabled FROM book_copy x JOIN book b ON b.id=x.book_id WHERE x.code=? FOR"
                + " UPDATE",
            text(v, "copyCode"));
    if (!copy.get("status").equals("AVAILABLE") || ((Number) copy.get("enabled")).intValue() != 1)
      throw new BusinessException("该实体图书暂不可借");
    long cid = id(copy, "id");
    var now = LocalDateTime.now();
    db.jdbc.update("UPDATE book_copy SET status='BORROWED' WHERE id=?", cid);
    return Map.of(
        "id",
        db.insert(
            "loan",
            fields(
                "user_id",
                uid,
                "copy_id",
                cid,
                "borrowed_at",
                now,
                "due_at",
                now.plusDays(borrowDays),
                "status",
                "BORROWED",
                "operator_id",
                me())));
  }

  private Object returnBook(Map<String, Object> v) {
    UserContext.requireAdmin();
    Map<String, Object> loan;
    if (v.containsKey("id")) loan = db.one("SELECT * FROM loan WHERE id=? FOR UPDATE", id(v, "id"));
    else {
      required(v, "copyCode");
      var rows =
          db.rows(
              "SELECT l.* FROM loan l JOIN book_copy x ON x.id=l.copy_id WHERE x.code=? AND"
                  + " l.status='BORROWED' FOR UPDATE",
              text(v, "copyCode"));
      if (rows.isEmpty()) throw new BusinessException("未找到该馆藏编号的在借记录");
      loan = rows.get(0);
    }
    if (!loan.get("status").equals("BORROWED")) throw new BusinessException("该借阅记录已归还");
    var now = LocalDateTime.now();
    var due = (LocalDateTime) loan.get("dueAt");
    long days =
        now.isAfter(due)
            ? Math.max(1, (Duration.between(due, now).getSeconds() + 86399) / 86400)
            : 0;
    long lid = id(loan, "id");
    db.jdbc.update("UPDATE loan SET status='RETURNED',returned_at=? WHERE id=?", now, lid);
    db.jdbc.update("UPDATE book_copy SET status='AVAILABLE' WHERE id=?", id(loan, "copyId"));
    BigDecimal fine = finePerDay.multiply(BigDecimal.valueOf(days));
    if (days > 0)
      db.insert(
          "fine", fields("loan_id", lid, "amount", fine, "overdue_days", days, "status", "UNPAID"));
    return Map.of("overdueDays", days, "fine", fine);
  }

  private Object addBlacklist(Map<String, Object> v) {
    UserContext.requireAdmin();
    long uid = id(v, "userId");
    required(v, "reason");
    var a = db.one("SELECT role FROM account WHERE id=? FOR UPDATE", uid);
    if (!a.get("role").equals("STUDENT")) throw new BusinessException("请选择学生");
    if (db.count("SELECT COUNT(*) FROM blacklist WHERE user_id=? AND active=1", uid) > 0)
      throw new BusinessException("该学生已在黑名单中");
    return Map.of(
        "id",
        db.insert(
            "blacklist",
            fields("user_id", uid, "reason", text(v, "reason"), "active", 1, "operator_id", me())));
  }

  private Object removeBlacklist(Map<String, Object> v) {
    UserContext.requireAdmin();
    var row = db.one("SELECT * FROM blacklist WHERE id=?", id(v, "id"));
    db.one("SELECT id FROM account WHERE id=? FOR UPDATE", id(row, "userId"));
    db.jdbc.update("UPDATE blacklist SET active=0 WHERE id=?", id(v, "id"));
    return null;
  }

  private Object reserve(Map<String, Object> v) {
    long uid = UserContext.admin() ? id(v, "userId") : me();
    eligible(uid);
    long rid = id(v, "resourceId");
    var r = db.one("SELECT * FROM resource WHERE id=? FOR UPDATE", rid);
    if (!r.get("status").equals("OPEN")
        || "SEAT".equals(r.get("kind")) && "DAMAGED".equals(r.get("equipmentStatus")))
      throw new BusinessException("该资源暂未开放或设备损坏");
    var start = time(v, "startAt");
    var end = time(v, "endAt");
    var now = LocalDateTime.now();
    if (start.isBefore(now.minusMinutes(1)) || !end.isAfter(start))
      throw new BusinessException("请选择有效的未来使用时间");
    String kind = r.get("kind").toString();
    if (kind.equals("LOCKER")
        && Duration.between(start, end).compareTo(Duration.ofDays(lockerMaxDays)) > 0)
      throw new BusinessException("储物柜使用期限最多" + lockerMaxDays + "天");
    if (kind.equals("SEAT")
        && (!start.toLocalDate().equals(end.toLocalDate())
            || Duration.between(start, end).compareTo(Duration.ofHours(4)) > 0))
      throw new BusinessException("座位预约应在同一天且最多4小时");
    if (kind.equals("ROOM")) required(v, "purpose");
    checkRoom(db, rid, start, end, null, null);
    if (db.count(
            "SELECT COUNT(*) FROM reservation WHERE user_id=? AND resource_id=? AND"
                + " status='PENDING' AND start_at<? AND end_at>?",
            uid,
            rid,
            end,
            start)
        > 0) throw new BusinessException("已有同一时段的待审核申请");
    if (db.count(
            "SELECT COUNT(*) FROM reservation v JOIN resource r ON r.id=v.resource_id WHERE"
                + " v.user_id=? AND r.kind=? AND v.status IN ('APPROVED','CHECKED_IN') AND"
                + " v.start_at<? AND v.end_at>?",
            uid,
            kind,
            end,
            start)
        > 0) throw new BusinessException("本人已有同类型资源的重叠预约");
    return Map.of(
        "id",
        db.insert(
            "reservation",
            fields(
                "user_id",
                uid,
                "resource_id",
                rid,
                "start_at",
                start,
                "end_at",
                end,
                "purpose",
                text(v, "purpose"),
                "organization",
                text(v, "organization"),
                "status",
                kind.equals("SEAT") ? "APPROVED" : "PENDING",
                "violation",
                0)));
  }

  private Object review(Map<String, Object> v) {
    UserContext.requireAdmin();
    var row = db.one("SELECT * FROM reservation WHERE id=? FOR UPDATE", id(v, "id"));
    if (!row.get("status").equals("PENDING")) throw new BusinessException("该申请已处理");
    if (!Set.of("APPROVED", "REJECTED").contains(text(v, "status")))
      throw new BusinessException("审核结果错误");
    if (text(v, "status").equals("APPROVED")) {
      eligible(id(row, "userId"));
      var r = db.one("SELECT * FROM resource WHERE id=? FOR UPDATE", id(row, "resourceId"));
      if (!r.get("status").equals("OPEN")) throw new BusinessException("资源当前未开放");
      if (!((LocalDateTime) row.get("endAt")).isAfter(LocalDateTime.now()))
        throw new BusinessException("申请时段已结束");
      checkRoom(
          db,
          id(row, "resourceId"),
          (LocalDateTime) row.get("startAt"),
          (LocalDateTime) row.get("endAt"),
          null,
          id(row, "id"));
      String kind = r.get("kind").toString();
      if (db.count(
              "SELECT COUNT(*) FROM reservation v JOIN resource r ON r.id=v.resource_id WHERE"
                  + " v.user_id=? AND r.kind=? AND v.id<>? AND v.status IN"
                  + " ('APPROVED','CHECKED_IN') AND v.start_at<? AND v.end_at>?",
              id(row, "userId"),
              kind,
              id(row, "id"),
              row.get("endAt"),
              row.get("startAt"))
          > 0) throw new BusinessException("该学生已有同类型资源的重叠预约");
    }
    db.jdbc.update(
        "UPDATE reservation SET status=?,reviewed_by=?,review_note=? WHERE id=?",
        text(v, "status"),
        me(),
        text(v, "reviewNote"),
        id(row, "id"));
    return null;
  }

  private Object cancelReservation(Map<String, Object> v) {
    var row = db.one("SELECT * FROM reservation WHERE id=? FOR UPDATE", id(v, "id"));
    owner(row);
    if (!Set.of("PENDING", "APPROVED").contains(row.get("status")))
      throw new BusinessException("当前状态不支持取消");
    db.jdbc.update("UPDATE reservation SET status='CANCELLED' WHERE id=?", id(row, "id"));
    return null;
  }

  private Object checkin(Map<String, Object> v) {
    var row =
        db.one(
            "SELECT v.*,r.kind,r.status AS resource_status,r.equipment_status FROM reservation v"
                + " JOIN resource r ON r.id=v.resource_id WHERE v.id=? FOR UPDATE",
            id(v, "id"));
    owner(row);
    eligible(id(row, "userId"));
    if (!row.get("status").equals("APPROVED")
        || !row.get("kind").equals("SEAT")
        || !row.get("resourceStatus").equals("OPEN")
        || "DAMAGED".equals(row.get("equipmentStatus"))) throw new BusinessException("当前记录不支持签到");
    var now = LocalDateTime.now();
    var start = (LocalDateTime) row.get("startAt");
    if (now.isBefore(start)
        || !now.isBefore(start.plusMinutes(grace))
        || !now.isBefore((LocalDateTime) row.get("endAt")))
      throw new BusinessException("请在预约开始后" + grace + "分钟内签到");
    db.jdbc.update(
        "UPDATE reservation SET status='CHECKED_IN',checked_in_at=? WHERE id=?",
        now,
        id(row, "id"));
    return null;
  }

  private Object register(Map<String, Object> v) {
    long uid = UserContext.admin() ? id(v, "userId") : me();
    eligible(uid);
    long aid = id(v, "activityId");
    var a = db.one("SELECT * FROM activity WHERE id=? FOR UPDATE", aid);
    if (!a.get("status").equals("OPEN")
        || !((LocalDateTime) a.get("startAt")).isAfter(LocalDateTime.now()))
      throw new BusinessException("活动报名已结束");
    var old = db.rows("SELECT * FROM registration WHERE activity_id=? AND user_id=?", aid, uid);
    if (!old.isEmpty() && !old.get(0).get("status").equals("CANCELLED"))
      throw new BusinessException("已报名此活动");
    if (db.count(
            "SELECT COUNT(*) FROM registration WHERE activity_id=? AND status<>'CANCELLED'", aid)
        >= id(a, "maxPeople")) throw new BusinessException("活动报名人数已满");
    if (old.isEmpty())
      return Map.of(
          "id",
          db.insert(
              "registration", fields("activity_id", aid, "user_id", uid, "status", "REGISTERED")));
    db.jdbc.update(
        "UPDATE registration SET status='REGISTERED',checked_in_at=NULL WHERE id=?",
        id(old.get(0), "id"));
    return null;
  }

  private Object cancelRegistration(Map<String, Object> v) {
    var row =
        db.one(
            "SELECT g.*,a.start_at FROM registration g JOIN activity a ON a.id=g.activity_id WHERE"
                + " g.id=? FOR UPDATE",
            id(v, "id"));
    owner(row);
    if (!row.get("status").equals("REGISTERED")
        || !((LocalDateTime) row.get("startAt")).isAfter(LocalDateTime.now()))
      throw new BusinessException("当前状态不支持取消报名");
    db.jdbc.update("UPDATE registration SET status='CANCELLED' WHERE id=?", id(row, "id"));
    return null;
  }

  private Object activityCheckin(Map<String, Object> v) {
    UserContext.requireAdmin();
    var row =
        db.one(
            "SELECT g.*,a.status AS activity_status,a.start_at,a.end_at FROM registration g JOIN"
                + " activity a ON a.id=g.activity_id WHERE g.id=? FOR UPDATE",
            id(v, "id"));
    var now = LocalDateTime.now();
    if (!row.get("status").equals("REGISTERED") || row.get("activityStatus").equals("CANCELLED"))
      throw new BusinessException("当前状态不支持核销");
    if (now.isBefore(((LocalDateTime) row.get("startAt")).minusMinutes(30))
        || now.isAfter((LocalDateTime) row.get("endAt")))
      throw new BusinessException("核销时间为活动开始前30分钟至活动结束");
    db.jdbc.update(
        "UPDATE registration SET status='CHECKED_IN',checked_in_at=? WHERE id=?", now, id(v, "id"));
    return null;
  }

  private Object visit(Map<String, Object> v) {
    long uid = UserContext.admin() ? (v.get("userId") == null ? 0 : id(v, "userId")) : me();
    if (uid > 0) eligible(uid);
    else {
      UserContext.requireAdmin();
      required(v, "visitorName", "identityRef");
    }
    return Map.of(
        "id",
        db.insert(
            "visit",
            fields(
                "user_id",
                uid > 0 ? uid : null,
                "visitor_name",
                uid > 0 ? null : text(v, "visitorName"),
                "identity_ref",
                uid > 0 ? null : text(v, "identityRef"),
                "entered_at",
                LocalDateTime.now(),
                "operator_id",
                me())));
  }

  private Object settleFine(Map<String, Object> v) {
    UserContext.requireAdmin();
    if (db.jdbc.update(
            "UPDATE fine SET status='SETTLED' WHERE id=? AND status='UNPAID'", id(v, "id"))
        != 1) throw new BusinessException("罚金已处理或记录不存在");
    return null;
  }

  @Transactional
  public void expireReservations() {
    var now = LocalDateTime.now();
    db.jdbc.update(
        "UPDATE reservation SET status='EXPIRED',violation=1 WHERE status='APPROVED' AND"
            + " start_at<=? AND resource_id IN (SELECT id FROM resource WHERE kind='SEAT')",
        now.minusMinutes(grace));
    db.jdbc.update(
        "UPDATE reservation SET status='COMPLETED' WHERE status IN ('APPROVED','CHECKED_IN') AND"
            + " end_at<=?",
        now);
    db.jdbc.update(
        "UPDATE reservation SET status='EXPIRED' WHERE status='PENDING' AND end_at<=?", now);
    db.jdbc.update("DELETE FROM auth_token WHERE expires_at<?", now);
  }

  public Object dashboard() {
    Map<String, Object> data = new LinkedHashMap<>();
    var now = LocalDateTime.now();
    var today = now.toLocalDate().atStartOfDay();
    if (!UserContext.admin()) {
      data.put(
          "activeLoans",
          db.count("SELECT COUNT(*) FROM loan WHERE user_id=? AND status='BORROWED'", me()));
      data.put(
          "overdueLoans",
          db.count(
              "SELECT COUNT(*) FROM loan WHERE user_id=? AND status='BORROWED' AND due_at<?",
              me(),
              now));
      data.put(
          "activeReservations",
          db.count(
              "SELECT COUNT(*) FROM reservation WHERE user_id=? AND status IN"
                  + " ('PENDING','APPROVED','CHECKED_IN') AND end_at>?",
              me(),
              now));
      data.put(
          "registrations",
          db.count(
              "SELECT COUNT(*) FROM registration WHERE user_id=? AND status='REGISTERED'", me()));
      data.put(
          "blacklisted",
          db.count("SELECT COUNT(*) FROM blacklist WHERE user_id=? AND active=1", me()) > 0);
      data.put(
          "violations",
          db.count("SELECT COUNT(*) FROM reservation WHERE user_id=? AND violation=1", me()));
    } else {
      data.put("books", db.count("SELECT COUNT(*) FROM book WHERE enabled=1"));
      data.put("copies", db.count("SELECT COUNT(*) FROM book_copy"));
      data.put("activeLoans", db.count("SELECT COUNT(*) FROM loan WHERE status='BORROWED'"));
      data.put(
          "overdueLoans",
          db.count("SELECT COUNT(*) FROM loan WHERE status='BORROWED' AND due_at<?", now));
      data.put(
          "pendingReservations",
          db.count("SELECT COUNT(*) FROM reservation WHERE status='PENDING'"));
      data.put(
          "todayVisits",
          db.count(
              "SELECT COUNT(*) FROM visit WHERE entered_at>=? AND entered_at<?",
              today,
              today.plusDays(1)));
      data.put(
          "todayUniqueVisitors",
          db.count(
              "SELECT COUNT(DISTINCT CONCAT(CASE WHEN user_id IS NULL THEN 'G' ELSE 'S'"
                  + " END,COALESCE(CAST(user_id AS CHAR),identity_ref))) FROM visit WHERE"
                  + " entered_at>=? AND entered_at<?",
              today,
              today.plusDays(1)));
      var months = new LinkedHashMap<String, Long>();
      for (int i = 5; i >= 0; i--) months.put(YearMonth.now().minusMonths(i).toString(), 0L);
      for (var l :
          db.rows(
              "SELECT borrowed_at FROM loan WHERE borrowed_at>=?",
              today.withDayOfMonth(1).minusMonths(5))) {
        String key = YearMonth.from((LocalDateTime) l.get("borrowedAt")).toString();
        months.computeIfPresent(key, (k, n) -> n + 1);
      }
      data.put(
          "monthlyLoans",
          months.entrySet().stream()
              .map(e -> Map.of("name", e.getKey(), "value", e.getValue()))
              .toList());
      data.put(
          "topBooks",
          db.rows(
              "SELECT b.title AS name,COUNT(*) AS `value` FROM loan l JOIN book_copy x ON"
                  + " x.id=l.copy_id JOIN book b ON b.id=x.book_id GROUP BY b.id,b.title ORDER BY"
                  + " `value` DESC LIMIT 5"));
      data.put(
          "categoryShare",
          db.rows(
              "SELECT COALESCE(c.name,'未分类') AS name,COUNT(*) AS `value` FROM loan l JOIN book_copy"
                  + " x ON x.id=l.copy_id JOIN book b ON b.id=x.book_id LEFT JOIN category c ON"
                  + " c.id=b.category_id GROUP BY c.name ORDER BY `value` DESC"));
    }
    data.put(
        "announcements",
        db.rows(
            "SELECT id,title,content,pinned,created_at FROM announcement ORDER BY pinned DESC,id"
                + " DESC LIMIT 5"));
    data.put(
        "rules",
        Map.of(
            "borrowDays",
            borrowDays,
            "maxLoans",
            maxLoans,
            "finePerDay",
            finePerDay,
            "lockerMaxDays",
            lockerMaxDays,
            "checkinGraceMinutes",
            grace));
    return data;
  }

  @Transactional
  public Object importStudents(InputStream input) {
    UserContext.requireAdmin();
    try (Workbook workbook = WorkbookFactory.create(input)) {
      if (workbook.getNumberOfSheets() == 0) throw new BusinessException("Excel没有工作表");
      Sheet sheet = workbook.getSheetAt(0);
      if (sheet.getLastRowNum() > 1000) throw new BusinessException("一次最多导入1000名学生");
      DataFormatter fmt = new DataFormatter();
      String[] headers = {"学号", "姓名", "院系", "联系方式", "初始密码"};
      Row header = sheet.getRow(0);
      if (header == null) throw new BusinessException("缺少表头");
      for (int i = 0; i < headers.length; i++)
        if (!headers[i].equals(fmt.formatCellValue(header.getCell(i)).trim()))
          throw new BusinessException("表头应为：学号、姓名、院系、联系方式、初始密码");
      List<Map<String, Object>> records = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      List<String> errors = new ArrayList<>();
      for (int n = 1; n <= sheet.getLastRowNum(); n++) {
        Row r = sheet.getRow(n);
        if (r == null) continue;
        String[] cells = new String[5];
        for (int i = 0; i < 5; i++) cells[i] = fmt.formatCellValue(r.getCell(i)).trim();
        if (Arrays.stream(cells).allMatch(String::isBlank)) continue;
        String prefix = "第" + (n + 1) + "行：";
        if (cells[0].isBlank()
            || cells[1].isBlank()
            || cells[4].length() < 8
            || cells[4].length() > 72) {
          errors.add(prefix + "学号、姓名必填，密码应为8到72位");
          continue;
        }
        if (!seen.add(cells[0])
            || db.count(
                    "SELECT COUNT(*) FROM account WHERE username=? OR student_no=?",
                    cells[0],
                    cells[0])
                > 0) {
          errors.add(prefix + "学号或账号重复");
          continue;
        }
        records.add(
            fields(
                "username",
                cells[0],
                "student_no",
                cells[0],
                "name",
                cells[1],
                "department",
                cells[2],
                "phone",
                cells[3],
                "password_hash",
                AuthServiceImpl.PASSWORDS.encode(cells[4]),
                "role",
                "STUDENT",
                "enabled",
                1));
      }
      if (!errors.isEmpty())
        throw new BusinessException(String.join("；", errors.stream().limit(15).toList()));
      for (var r : records) db.insert("account", r);
      return Map.of("imported", records.size());
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException("Excel解析失败，请使用正确的xlsx模板");
    }
  }
}
