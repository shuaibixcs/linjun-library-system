package edu.linjun.library.config;

import static edu.linjun.library.common.Database.fields;

import edu.linjun.library.common.Database;
import edu.linjun.library.service.impl.AuthServiceImpl;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoDataInitializer implements ApplicationRunner {
  private final Database db;

  @Value("${library.demo-seed:true}")
  private boolean enabled;

  @Value("${library.demo-password:Library123!}")
  private String password;

  public DemoDataInitializer(Database db) {
    this.db = db;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled || db.count("SELECT COUNT(*) FROM account") > 0) return;
    String hash = AuthServiceImpl.PASSWORDS.encode(password);
    long superId =
        db.insert(
            "account",
            fields(
                "username",
                "superadmin",
                "password_hash",
                hash,
                "name",
                "超级管理员",
                "role",
                "SUPER_ADMIN",
                "enabled",
                1));
    long admin =
        db.insert(
            "account",
            fields(
                "username",
                "admin",
                "password_hash",
                hash,
                "name",
                "林老师",
                "role",
                "ADMIN",
                "enabled",
                1));
    List<Long> students = new ArrayList<>();
    String[] names = {"陈同学", "林同学", "周同学", "许同学", "宋同学"};
    for (int i = 0; i < names.length; i++)
      students.add(
          db.insert(
              "account",
              fields(
                  "username",
                  "202600" + (i + 1),
                  "password_hash",
                  hash,
                  "name",
                  names[i],
                  "role",
                  "STUDENT",
                  "enabled",
                  1,
                  "student_no",
                  "202600" + (i + 1),
                  "department",
                  i < 3 ? "计算机学院" : "人文学院",
                  "phone",
                  "")));
    String[] categories = {"计算机科学", "文学与小说", "社会科学", "自然科学", "艺术与设计", "历史与文化"};
    List<Long> cats = new ArrayList<>();
    for (String c : categories)
      cats.add(db.insert("category", fields("name", c, "description", c + "馆藏")));
    String[][] books = {
      {"9787111641247", "深入理解计算机系统", "Randal E. Bryant", "机械工业出版社", "0"},
      {"9787115546081", "Java核心技术 卷I", "Cay S. Horstmann", "机械工业出版社", "0"},
      {"9787115587206", "Vue.js设计与实现", "霍春阳", "人民邮电出版社", "0"},
      {"9787111213826", "算法导论", "Thomas H. Cormen", "机械工业出版社", "0"},
      {"9787536692930", "三体", "刘慈欣", "重庆出版社", "1"},
      {"9787506365437", "活着", "余华", "作家出版社", "1"},
      {"9787532776770", "月亮与六便士", "毛姆", "上海译文出版社", "1"},
      {"9787508647357", "人类简史", "尤瓦尔·赫拉利", "中信出版社", "2"},
      {"9787535732309", "时间简史", "史蒂芬·霍金", "湖南科学技术出版社", "3"},
      {"9787508660752", "设计心理学", "唐纳德·诺曼", "中信出版社", "4"},
      {"9787101003048", "史记", "司马迁", "中华书局", "5"},
      {"9787111421900", "数据库系统概念", "Abraham Silberschatz", "机械工业出版社", "0"}
    };
    List<Long> copies = new ArrayList<>();
    for (int i = 0; i < books.length; i++) {
      var b = books[i];
      long bid =
          db.insert(
              "book",
              fields(
                  "isbn",
                  b[0],
                  "title",
                  b[1],
                  "author",
                  b[2],
                  "publisher",
                  b[3],
                  "category_id",
                  cats.get(Integer.parseInt(b[4])),
                  "location",
                  (Integer.parseInt(b[4]) + 1) + "F · " + (i + 1) + "号书架",
                  "description",
                  "在阅读中发现新的世界。馆藏可供师生借阅。",
                  "enabled",
                  1));
      for (int j = 1; j <= 3; j++)
        copies.add(
            db.insert(
                "book_copy",
                fields(
                    "book_id",
                    bid,
                    "code",
                    String.format("LJ-%03d-%02d", i + 1, j),
                    "status",
                    "AVAILABLE")));
      db.insert(
          "acquisition",
          fields("book_id", bid, "quantity", 3, "source", "课程演示初始化", "operator_id", admin));
    }
    for (int i = 1; i <= 8; i++)
      db.insert(
          "resource",
          fields(
              "kind",
              "SEAT",
              "code",
              String.format("E-%02d", i),
              "name",
              "电子阅览座位 " + i,
              "location",
              "2F · 电子阅览室",
              "capacity",
              1,
              "status",
              i == 8 ? "MAINTENANCE" : "OPEN",
              "equipment_status",
              i == 8 ? "DAMAGED" : "NORMAL",
              "description",
              "配备电脑、电源及网络"));
    for (int i = 1; i <= 6; i++)
      db.insert(
          "resource",
          fields(
              "kind",
              "LOCKER",
              "code",
              String.format("L-%02d", i),
              "name",
              "储物柜 " + i,
              "location",
              "1F · 大厅东侧",
              "capacity",
              1,
              "status",
              "OPEN",
              "description",
              "个人学习物品临时存放"));
    List<Long> rooms = new ArrayList<>();
    String[] roomNames = {"静思自习室", "明理研讨室", "知行活动室"};
    for (int i = 0; i < 3; i++)
      rooms.add(
          db.insert(
              "resource",
              fields(
                  "kind",
                  "ROOM",
                  "code",
                  "R-" + (i + 1),
                  "name",
                  roomNames[i],
                  "location",
                  (i + 2) + "F · " + (301 + i),
                  "capacity",
                  20 + i * 20,
                  "status",
                  "OPEN",
                  "description",
                  "提供投影、白板及无线网络")));
    var tomorrow = LocalDate.now().plusDays(1).atTime(14, 0);
    String[] titles = {"阅读的力量 · 秋日读书会", "从零认识人工智能", "传统文化与现代生活"};
    for (int i = 0; i < 3; i++)
      db.insert(
          "activity",
          fields(
              "title",
              titles[i],
              "description",
              "与同学们分享观点，拓展视野。欢迎提前报名，活动开始前请到现场核销。",
              "resource_id",
              rooms.get(i),
              "start_at",
              tomorrow.plusDays(i),
              "end_at",
              tomorrow.plusDays(i).plusHours(2),
              "max_people",
              20,
              "status",
              "OPEN"));
    db.insert(
        "announcement",
        fields(
            "title",
            "欢迎使用林间图书馆服务平台",
            "content",
            "这里汇集图书借阅、学习空间预约和校园阅读活动。学生可在个人中心查看借阅、预约和报名记录。",
            "pinned",
            1,
            "author_id",
            admin));
    db.insert(
        "announcement",
        fields(
            "title",
            "秋季学期开放时间公告",
            "content",
            "图书馆开放时间为每日 08:00—22:00。电子阅览室座位需提前预约，并在开始后30分钟内签到。",
            "pinned",
            0,
            "author_id",
            admin));
    db.insert(
        "announcement",
        fields(
            "title",
            "文明借阅与空间使用提醒",
            "content",
            "请按时归还图书，保持公共空间整洁。储物柜最长使用30天，房间申请请说明用途。",
            "pinned",
            0,
            "author_id",
            admin));
    for (int month = 5; month >= 0; month--)
      for (int n = 0; n < 8 + (5 - month) * 3; n++) {
        var start = LocalDate.now().withDayOfMonth(1).minusMonths(month).atTime(10, 0).plusHours(n);
        db.insert(
            "loan",
            fields(
                "user_id",
                students.get(n % 5),
                "copy_id",
                copies.get(n % copies.size()),
                "borrowed_at",
                start,
                "due_at",
                start.plusDays(30),
                "returned_at",
                start.plusHours(2),
                "status",
                "RETURNED",
                "operator_id",
                admin));
      }
    var now = LocalDateTime.now();
    db.insert(
        "loan",
        fields(
            "user_id",
            students.get(0),
            "copy_id",
            copies.get(0),
            "borrowed_at",
            now.minusDays(4),
            "due_at",
            now.plusDays(26),
            "status",
            "BORROWED",
            "operator_id",
            admin));
    db.jdbc.update("UPDATE book_copy SET status='BORROWED' WHERE id=?", copies.get(0));
    db.insert(
        "loan",
        fields(
            "user_id",
            students.get(1),
            "copy_id",
            copies.get(3),
            "borrowed_at",
            now.minusDays(33),
            "due_at",
            now.minusDays(3),
            "status",
            "BORROWED",
            "operator_id",
            admin));
    db.jdbc.update("UPDATE book_copy SET status='BORROWED' WHERE id=?", copies.get(3));
    for (int i = 0; i < 12; i++)
      db.insert(
          "visit",
          fields(
              "user_id",
              students.get(i % 5),
              "entered_at",
              LocalDate.now().atTime(8, 0).plusMinutes(i * 17),
              "operator_id",
              admin));
  }
}
