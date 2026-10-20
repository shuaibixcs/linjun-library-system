package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("loan")
public class Loan {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private Long copyId;
  private LocalDateTime borrowedAt;
  private LocalDateTime dueAt;
  private LocalDateTime returnedAt;
  private String status;
  private Long operatorId;
  private LocalDateTime createdAt;
}
