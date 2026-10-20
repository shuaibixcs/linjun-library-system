package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("category")
public class Category {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String description;
  private LocalDateTime createdAt;
}
