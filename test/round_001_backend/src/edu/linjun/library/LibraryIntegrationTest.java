package edu.linjun.library;

import static edu.linjun.library.common.Database.fields;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import edu.linjun.library.common.*;
import edu.linjun.library.mapper.AccountMapper;
import edu.linjun.library.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LibraryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired Database db;
  @Autowired WorkflowService workflow;
  @Autowired AccountMapper accounts;
  String admin, student, other, superadmin;

  @BeforeEach
  void login() throws Exception {
    admin = loginAs("admin");
    student = loginAs("2026001");
    other = loginAs("2026002");
    superadmin = loginAs("superadmin");
  }

  String loginAs(String username) throws Exception {
    return call(
            "POST",
            "/api/auth/login",
            null,
            Map.of("username", username, "password", "Library123!"),
            200)
        .path("token")
        .asText();
  }

  long uid(String username) {
    return db.count("SELECT id FROM account WHERE username=?", username);
  }

  JsonNode call(String method, String path, String token, Object body, int status)
      throws Exception {
    var req =
        MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path)
            .contentType(MediaType.APPLICATION_JSON);
    if (token != null) req.header("Authorization", "Bearer " + token);
    if (body != null) req.content(json.writeValueAsBytes(body));
    var result = mvc.perform(req).andReturn();
    assertThat(result.getResponse().getStatus())
        .withFailMessage(
            "%s %s expected %s got %s: %s",
            method,
            path,
            status,
            result.getResponse().getStatus(),
            result.getResponse().getContentAsString())
        .isEqualTo(status);
    return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
  }

  JsonNode action(String name, String token, Object values, int status) throws Exception {
    return call("POST", "/api/actions/" + name, token, values, status);
  }

  Map<String, Object> reservation(long rid, LocalDateTime start, LocalDateTime end) {
    return fields(
        "resourceId", rid, "startAt", start.toString(), "endAt", end.toString(), "purpose", "课程学习");
  }

  @Test
  void unauthenticatedAndWrongPasswordAreRejected() throws Exception {
    call("GET", "/api/dashboard", null, null, 401);
    call("POST", "/api/auth/login", null, Map.of("username", "admin", "password", "bad"), 401);
  }

  @Test
  void studentsCannotManagePeopleOrAccessOtherLoans() throws Exception {
    call("GET", "/api/data/students", student, null, 403);
    call("POST", "/api/data/books", student, Map.of("title", "bad"), 403);
    var r = call("GET", "/api/data/loans?size=100", student, null, 200);
    for (var loan : r.path("records"))
      assertThat(loan.path("userId").asLong()).isEqualTo(uid("2026001"));
  }

  @Test
  void administratorManagementRequiresSuperRole() throws Exception {
    call("GET", "/api/data/administrators", admin, null, 403);
    call("GET", "/api/data/administrators", superadmin, null, 200);
  }

  @Test
  void booksAndOptionsAndDashboardAreUsable() throws Exception {
    assertThat(
            call("GET", "/api/data/books?page=1&size=5&q=Java", student, null, 200)
                .path("total")
                .asInt())
        .isEqualTo(1);
    var dash = call("GET", "/api/dashboard", admin, null, 200);
    assertThat(dash.path("monthlyLoans").size()).isEqualTo(6);
    assertThat(dash.path("todayVisits").asInt()).isEqualTo(12);
    assertThat(dash.path("todayUniqueVisitors").asInt()).isEqualTo(5);
    call("GET", "/api/options", student, null, 200);
  }

  @Test
  void acquireBorrowReturnAndRepeatedReturnMaintainInventory() throws Exception {
    long bid = db.count("SELECT id FROM book WHERE title='三体'");
    action("acquire", admin, Map.of("bookId", bid, "quantity", 2, "source", "测试采购"), 200);
    String code =
        db.one(
                "SELECT code FROM book_copy WHERE book_id=? AND status='AVAILABLE' ORDER BY id DESC"
                    + " LIMIT 1",
                bid)
            .get("code")
            .toString();
    long lid =
        action("borrow", admin, Map.of("userId", uid("2026001"), "copyCode", code), 200)
            .path("id")
            .asLong();
    assertThat(db.count("SELECT COUNT(*) FROM book_copy WHERE code=? AND status='BORROWED'", code))
        .isEqualTo(1);
    action("borrow", admin, Map.of("userId", uid("2026002"), "copyCode", code), 400);
    action("return", admin, Map.of("id", lid), 200);
    action("return", admin, Map.of("id", lid), 400);
    assertThat(db.count("SELECT COUNT(*) FROM book_copy WHERE code=? AND status='AVAILABLE'", code))
        .isEqualTo(1);
  }

  @Test
  void overdueReturnCreatesOneFineEvenForBlacklistedStudent() throws Exception {
    long user = uid("2026002");
    long lid = db.count("SELECT id FROM loan WHERE user_id=? AND status='BORROWED'", user);
    db.jdbc.update(
        "UPDATE loan SET due_at=? WHERE id=?",
        LocalDateTime.now().minusDays(2).minusMinutes(1),
        lid);
    action("blacklist-add", admin, Map.of("userId", user, "reason", "测试违规"), 200);
    var returned = action("return", admin, Map.of("id", lid), 200);
    assertThat(returned.path("overdueDays").asInt()).isEqualTo(3);
    assertThat(returned.path("fine").asDouble()).isEqualTo(1.5);
    action("return", admin, Map.of("id", lid), 400);
    assertThat(db.count("SELECT COUNT(*) FROM fine WHERE loan_id=?", lid)).isEqualTo(1);
  }

  @Test
  void blacklistBlocksBorrowReserveAndEntry() throws Exception {
    long uid = uid("2026001");
    action("blacklist-add", admin, Map.of("userId", uid, "reason", "测试"), 200);
    action("borrow", admin, Map.of("userId", uid, "copyCode", "LJ-001-02"), 400);
    long seat = db.count("SELECT id FROM resource WHERE code='E-01'");
    var start = LocalDate.now().plusDays(1).atTime(8, 0);
    action("reserve", student, reservation(seat, start, start.plusHours(4)), 400);
    action("visit", student, Map.of(), 400);
  }

  @Test
  void seatConflictsAndForeignCancellationAreBlocked() throws Exception {
    long seat = db.count("SELECT id FROM resource WHERE code='E-01'");
    var start = LocalDate.now().plusDays(1).atTime(8, 0);
    long rid =
        action("reserve", student, reservation(seat, start, start.plusHours(4)), 200)
            .path("id")
            .asLong();
    action("reserve", other, reservation(seat, start.plusHours(1), start.plusHours(3)), 400);
    action("reservation-cancel", other, Map.of("id", rid), 403);
    action("reservation-checkin", student, Map.of("id", rid), 400);
    action("reservation-cancel", student, Map.of("id", rid), 200);
    action("reserve", other, reservation(seat, start, start.plusHours(4)), 200);
  }

  @Test
  void pendingRoomReviewChecksActivitiesAndAdjacentSlots() throws Exception {
    long room = db.count("SELECT id FROM resource WHERE code='R-1'");
    var activity = db.one("SELECT * FROM activity WHERE resource_id=? LIMIT 1", room);
    var start = (LocalDateTime) activity.get("startAt");
    action("reserve", student, reservation(room, start, start.plusHours(1)), 400);
    var later = start.plusDays(5);
    long rid =
        action("reserve", student, reservation(room, later, later.plusHours(1)), 200)
            .path("id")
            .asLong();
    long rid2 =
        action("reserve", other, reservation(room, later, later.plusHours(1)), 200)
            .path("id")
            .asLong();
    action("review", admin, Map.of("id", rid, "status", "APPROVED"), 200);
    action("review", admin, Map.of("id", rid2, "status", "APPROVED"), 400);
    action("reserve", other, reservation(room, later.plusHours(1), later.plusHours(2)), 200);
  }

  @Test
  void expiryCancelsNoShowAndReleasesLocker() throws Exception {
    long user = uid("2026001"),
        seat = db.count("SELECT id FROM resource WHERE code='E-01'"),
        locker = db.count("SELECT id FROM resource WHERE code='L-01'");
    var now = LocalDateTime.now();
    long noShow =
        db.insert(
            "reservation",
            fields(
                "user_id",
                user,
                "resource_id",
                seat,
                "start_at",
                now.minusHours(1),
                "end_at",
                now.plusHours(2),
                "status",
                "APPROVED",
                "violation",
                0));
    long expiredLocker =
        db.insert(
            "reservation",
            fields(
                "user_id",
                user,
                "resource_id",
                locker,
                "start_at",
                now.minusDays(3),
                "end_at",
                now.minusMinutes(1),
                "status",
                "APPROVED",
                "violation",
                0));
    workflow.expireReservations();
    workflow.expireReservations();
    assertThat(db.one("SELECT * FROM reservation WHERE id=?", noShow).get("status"))
        .isEqualTo("EXPIRED");
    assertThat(db.one("SELECT * FROM reservation WHERE id=?", expiredLocker).get("status"))
        .isEqualTo("COMPLETED");
    assertThat(db.count("SELECT COUNT(*) FROM reservation WHERE id=? AND violation=1", noShow))
        .isEqualTo(1);
  }

  @Test
  void registrationEnforcesCapacityAndAllowsReRegistrationAfterCancellation() throws Exception {
    long aid = db.count("SELECT MIN(id) FROM activity");
    db.jdbc.update("UPDATE activity SET max_people=1 WHERE id=?", aid);
    long id = action("register", student, Map.of("activityId", aid), 200).path("id").asLong();
    action("register", student, Map.of("activityId", aid), 400);
    action("register", other, Map.of("activityId", aid), 400);
    action("registration-cancel", student, Map.of("id", id), 200);
    action("register", other, Map.of("activityId", aid), 200);
  }

  @Test
  void resourceAvailabilityOmitsStudentIdentity() throws Exception {
    long seat = db.count("SELECT id FROM resource WHERE code='E-01'");
    var start = LocalDate.now().plusDays(1).atTime(8, 0);
    action("reserve", student, reservation(seat, start, start.plusHours(2)), 200);
    var available = call("GET", "/api/resources/" + seat + "/availability", other, null, 200);
    assertThat(available.size()).isEqualTo(1);
    assertThat(available.get(0).has("userId")).isFalse();
    assertThat(available.get(0).has("studentName")).isFalse();
  }

  @Test
  void accountCreationCannotEscalateRoleOrRevealPassword() throws Exception {
    var result =
        call(
            "POST",
            "/api/data/students",
            admin,
            fields(
                "username",
                "newstudent",
                "studentNo",
                "2099001",
                "name",
                "测试同学",
                "password",
                "Student123!",
                "role",
                "SUPER_ADMIN"),
            200);
    assertThat(result.path("role").asText()).isEqualTo("STUDENT");
    assertThat(result.path("passwordHash").isNull()).isTrue();
    var list = call("GET", "/api/data/students", admin, null, 200);
    for (var row : list.path("records")) assertThat(row.has("passwordHash")).isFalse();
  }

  @Test
  void importRejectsDuplicateRowsAtomically() throws Exception {
    try (var wb = new XSSFWorkbook();
        var out = new java.io.ByteArrayOutputStream()) {
      var sheet = wb.createSheet("学生");
      String[][] lines = {
        {"学号", "姓名", "院系", "联系方式", "初始密码"},
        {"2099009", "测试甲", "计算机学院", "", "Student123!"},
        {"2026001", "测试乙", "计算机学院", "", "Student123!"}
      };
      for (int i = 0; i < lines.length; i++) {
        var r = sheet.createRow(i);
        for (int j = 0; j < lines[i].length; j++) r.createCell(j).setCellValue(lines[i][j]);
      }
      wb.write(out);
      var file =
          new MockMultipartFile(
              "file",
              "students.xlsx",
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
              out.toByteArray());
      var result =
          mvc.perform(
                  MockMvcRequestBuilders.multipart("/api/students/import")
                      .file(file)
                      .header("Authorization", "Bearer " + admin))
              .andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertThat(db.count("SELECT COUNT(*) FROM account WHERE username='2099009'")).isEqualTo(0);
    }
  }

  @Test
  void loginSessionsExpireAfterPasswordChange() throws Exception {
    call(
        "POST",
        "/api/auth/password",
        student,
        Map.of("oldPassword", "Library123!", "newPassword", "Changed123!"),
        200);
    call("GET", "/api/auth/me", student, null, 401);
  }

  @Test
  void returnByCopyCodeAndFineSettlementWork() throws Exception {
    var result = action("return", admin, Map.of("copyCode", "LJ-002-01"), 200);
    assertThat(result.path("fine").asDouble()).isGreaterThan(0);
    long fid = db.count("SELECT MAX(id) FROM fine");
    action("fine-settle", student, Map.of("id", fid), 403);
    action("fine-settle", admin, Map.of("id", fid), 200);
    action("fine-settle", admin, Map.of("id", fid), 400);
  }

  @Test
  void allModulesSupportPagination() throws Exception {
    for (String m :
        List.of(
            "students",
            "categories",
            "books",
            "copies",
            "acquisitions",
            "loans",
            "fines",
            "blacklist",
            "resources",
            "reservations",
            "activities",
            "registrations",
            "announcements",
            "visits")) {
      var r = call("GET", "/api/data/" + m + "?page=1&size=2", admin, null, 200);
      assertThat(r.path("records").size()).isLessThanOrEqualTo(2);
    }
  }

  @Test
  void damagedSeatBlocksReservationAndCheckin() throws Exception {
    long seat = db.count("SELECT id FROM resource WHERE code='E-01'");
    var start = LocalDate.now().plusDays(1).atTime(8, 0);
    db.jdbc.update("UPDATE resource SET equipment_status='DAMAGED' WHERE id=?", seat);
    action("reserve", student, reservation(seat, start, start.plusHours(2)), 400);
    long rid =
        db.insert(
            "reservation",
            fields(
                "user_id",
                uid("2026001"),
                "resource_id",
                seat,
                "start_at",
                LocalDateTime.now().minusMinutes(2),
                "end_at",
                LocalDateTime.now().plusHours(1),
                "status",
                "APPROVED",
                "violation",
                0));
    action("reservation-checkin", student, Map.of("id", rid), 400);
  }

  @Test
  void ongoingActivityAppearsAsRoomOccupancy() throws Exception {
    long room = db.count("SELECT id FROM resource WHERE code='R-1'");
    db.jdbc.update(
        "UPDATE activity SET start_at=?,end_at=? WHERE resource_id=?",
        LocalDateTime.now().minusMinutes(5),
        LocalDateTime.now().plusHours(1),
        room);
    var result = call("GET", "/api/data/resources?kind=ROOM", student, null, 200);
    for (var r : result.path("records"))
      if (r.path("id").asLong() == room) {
        assertThat(r.path("occupied").asInt()).isEqualTo(1);
        assertThat(r.path("activityOccupied").asInt()).isEqualTo(1);
      }
  }

  int concurrentAttempts(String action, Map<String, Object> values) throws Exception {
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    List<Future<Boolean>> futures = new ArrayList<>();
    for (String username : List.of("2026001", "2026002")) {
      futures.add(
          pool.submit(
              () -> {
                gate.await();
                UserContext.set(
                    accounts.selectById(uid(action.equals("borrow") ? "admin" : username)));
                var payload = new LinkedHashMap<>(values);
                if (action.equals("borrow")) payload.put("userId", uid(username));
                try {
                  workflow.perform(action, payload);
                  return true;
                } catch (BusinessException e) {
                  return false;
                } finally {
                  UserContext.clear();
                }
              }));
    }
    gate.countDown();
    try {
      int successes = 0;
      for (var f : futures) if (f.get(20, TimeUnit.SECONDS)) successes++;
      return successes;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentBorrowAdmitsExactlyOneStudent() throws Exception {
    long bid = db.count("SELECT MIN(id) FROM book");
    long cid =
        db.insert(
            "book_copy", fields("book_id", bid, "code", "CONCURRENCY-COPY", "status", "AVAILABLE"));
    try {
      assertThat(concurrentAttempts("borrow", Map.of("copyCode", "CONCURRENCY-COPY"))).isEqualTo(1);
      assertThat(db.count("SELECT COUNT(*) FROM loan WHERE copy_id=?", cid)).isEqualTo(1);
    } finally {
      db.jdbc.update("DELETE FROM loan WHERE copy_id=?", cid);
      db.jdbc.update("DELETE FROM book_copy WHERE id=?", cid);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentRegistrationRespectsLastPlace() throws Exception {
    long rid =
        db.insert(
            "resource",
            fields(
                "kind",
                "ROOM",
                "code",
                "CONCURRENCY-ROOM",
                "name",
                "并发活动场地",
                "capacity",
                2,
                "status",
                "OPEN"));
    var start = LocalDate.now().plusDays(10).atTime(14, 0);
    long aid =
        db.insert(
            "activity",
            fields(
                "title",
                "并发报名测试",
                "resource_id",
                rid,
                "start_at",
                start,
                "end_at",
                start.plusHours(1),
                "max_people",
                1,
                "status",
                "OPEN"));
    try {
      assertThat(concurrentAttempts("register", Map.of("activityId", aid))).isEqualTo(1);
      assertThat(db.count("SELECT COUNT(*) FROM registration WHERE activity_id=?", aid))
          .isEqualTo(1);
    } finally {
      db.jdbc.update("DELETE FROM registration WHERE activity_id=?", aid);
      db.jdbc.update("DELETE FROM activity WHERE id=?", aid);
      db.jdbc.update("DELETE FROM resource WHERE id=?", rid);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentSeatReservationsAdmitExactlyOneStudent() throws Exception {
    long rid =
        db.insert(
            "resource",
            fields(
                "kind",
                "SEAT",
                "code",
                "CONCURRENCY-SEAT",
                "name",
                "并发测试座位",
                "capacity",
                1,
                "status",
                "OPEN"));
    var start = LocalDate.now().plusDays(10).atTime(8, 0);
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    List<Future<Boolean>> futures = new ArrayList<>();
    for (String username : List.of("2026001", "2026002"))
      futures.add(
          pool.submit(
              () -> {
                gate.await();
                UserContext.set(accounts.selectById(uid(username)));
                try {
                  workflow.perform("reserve", reservation(rid, start, start.plusHours(2)));
                  return true;
                } catch (BusinessException e) {
                  return false;
                } finally {
                  UserContext.clear();
                }
              }));
    gate.countDown();
    try {
      int success = 0;
      for (var f : futures) if (f.get(20, TimeUnit.SECONDS)) success++;
      assertThat(success).isEqualTo(1);
      assertThat(db.count("SELECT COUNT(*) FROM reservation WHERE resource_id=?", rid))
          .isEqualTo(1);
    } finally {
      pool.shutdownNow();
      db.jdbc.update("DELETE FROM reservation WHERE resource_id=?", rid);
      db.jdbc.update("DELETE FROM resource WHERE id=?", rid);
    }
  }
}
