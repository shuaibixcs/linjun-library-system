package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("resource")
public class Resource {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String kind;
  private String code;
  private String name;
  private String location;
  private Integer capacity;
  private String status;
  private String equipmentStatus;
  private String description;
  private LocalDateTime createdAt;
}
