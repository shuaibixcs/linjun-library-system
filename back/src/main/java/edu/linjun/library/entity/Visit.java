package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("visit")
public class Visit {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private String visitorName;
  private String identityRef;
  private LocalDateTime enteredAt;
  private Long operatorId;
  private LocalDateTime createdAt;
}
