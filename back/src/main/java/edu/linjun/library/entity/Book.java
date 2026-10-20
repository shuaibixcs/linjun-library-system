package edu.linjun.library.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("book")
public class Book {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String isbn;
  private String title;
  private String author;
  private String publisher;
  private Long categoryId;
  private String location;
  private String description;
  private Integer enabled;
  private LocalDateTime createdAt;
}
