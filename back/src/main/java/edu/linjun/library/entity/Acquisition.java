package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("acquisition")
public class Acquisition {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long bookId;
  private Integer quantity;
  private String source;
  private Long operatorId;
  private LocalDateTime createdAt;
}
