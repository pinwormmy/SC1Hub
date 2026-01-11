package com.sc1hub;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan(basePackages = {"com.sc1hub.board.mapper", "com.sc1hub.member.mapper", "com.sc1hub.visitor.mapper",
        "com.sc1hub.assistant.mapper"},
        annotationClass = Mapper.class)
@SpringBootApplication
@EnableScheduling
public class SC1HubApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(SC1HubApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder applicationBuilder) {
        return applicationBuilder.sources(ServletInitializer.class);
    }

}
