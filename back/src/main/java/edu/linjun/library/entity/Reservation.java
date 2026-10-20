package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("reservation")
public class Reservation {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private Long resourceId;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
  private String purpose;
  private String organization;
  private String status;
  private LocalDateTime checkedInAt;
  private Long reviewedBy;
  private String reviewNote;
  private Integer violation;
  private LocalDateTime createdAt;
}
