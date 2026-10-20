package edu.linjun.library;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("edu.linjun.library.mapper")
@EnableScheduling
public class LibraryApplication {
  public static void main(String[] args) {
    SpringApplication.run(LibraryApplication.class, args);
  }
}
