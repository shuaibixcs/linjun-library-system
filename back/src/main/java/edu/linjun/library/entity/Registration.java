package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("registration")
public class Registration {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long activityId;
  private Long userId;
  private String status;
  private LocalDateTime checkedInAt;
  private LocalDateTime createdAt;
}
