package edu.linjun.library.controller;

import edu.linjun.library.common.*;
import edu.linjun.library.service.WorkflowService;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class WorkflowController {
  private final WorkflowService service;
  private final Database db;

  public WorkflowController(WorkflowService service, Database db) {
    this.service = service;
    this.db = db;
  }

  @PostMapping("/actions/{action}")
  public Object action(@PathVariable String action, @RequestBody Map<String, Object> v) {
    return ApiResult.ok(service.perform(action, v));
  }

  @GetMapping("/dashboard")
  public Object dashboard() {
    return ApiResult.ok(service.dashboard());
  }

  @PostMapping("/students/import")
  public Object upload(@RequestParam("file") MultipartFile file) throws IOException {
    return ApiResult.ok(service.importStudents(file.getInputStream()));
  }

  @GetMapping("/resources/{id}/availability")
  public Object availability(@PathVariable long id) {
    db.one("SELECT id FROM resource WHERE id=?", id);
    return ApiResult.ok(
        db.rows(
            "SELECT start_at,end_at,'RESERVATION' AS source FROM reservation WHERE resource_id=?"
                + " AND status IN ('APPROVED','CHECKED_IN') AND end_at>CURRENT_TIMESTAMP UNION ALL"
                + " SELECT start_at,end_at,'ACTIVITY' AS source FROM activity WHERE resource_id=?"
                + " AND status<>'CANCELLED' AND end_at>CURRENT_TIMESTAMP ORDER BY start_at",
            id,
            id));
  }

  @GetMapping("/students/template")
  public org.springframework.http.ResponseEntity<byte[]> template() throws IOException {
    UserContext.requireAdmin();
    try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var out = new java.io.ByteArrayOutputStream()) {
      var sheet = wb.createSheet("学生导入");
      var row = sheet.createRow(0);
      String[] headers = {"学号", "姓名", "院系", "联系方式", "初始密码"};
      for (int i = 0; i < headers.length; i++) {
        row.createCell(i).setCellValue(headers[i]);
        sheet.setColumnWidth(i, 5500);
      }
      var sample = sheet.createRow(1);
      String[] cells = {"2026999", "演示学生", "计算机学院", "", "Student123!"};
      for (int i = 0; i < cells.length; i++) sample.createCell(i).setCellValue(cells[i]);
      wb.write(out);
      return org.springframework.http.ResponseEntity.ok()
          .header("Content-Disposition", "attachment; filename=student-import.xlsx")
          .contentType(
              org.springframework.http.MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(out.toByteArray());
    }
  }
}
