package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("blacklist")
public class Blacklist {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private String reason;
  private Integer active;
  private Long operatorId;
  private LocalDateTime createdAt;
}
