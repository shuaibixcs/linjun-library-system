package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("announcement")
public class Announcement {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String title;
  private String content;
  private Integer pinned;
  private Long authorId;
  private LocalDateTime createdAt;
}
