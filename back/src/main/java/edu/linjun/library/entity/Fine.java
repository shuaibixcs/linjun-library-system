package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("fine")
public class Fine {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long loanId;
  private BigDecimal amount;
  private Integer overdueDays;
  private String status;
  private LocalDateTime createdAt;
}
