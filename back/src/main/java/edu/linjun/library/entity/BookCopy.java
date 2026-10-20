package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("book_copy")
public class BookCopy {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long bookId;
  private String code;
  private String status;
  private LocalDateTime createdAt;
}
