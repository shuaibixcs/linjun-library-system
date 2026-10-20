package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("account")
public class Account {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String username;
  private String passwordHash;
  private String name;
  private String role;
  private Integer enabled;
  private String studentNo;
  private String department;
  private String phone;
  private LocalDateTime createdAt;
}
