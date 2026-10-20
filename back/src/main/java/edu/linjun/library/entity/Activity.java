package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("activity")
public class Activity {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String title;
  private String description;
  private Long resourceId;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
  private Integer maxPeople;
  private String status;
  private LocalDateTime createdAt;
}
