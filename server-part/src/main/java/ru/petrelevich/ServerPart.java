package ru.petrelevich;


import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ServerPart {
    public static void main(String[] args)  {
        SpringApplication.run(ServerPart.class, args);
        log.info("ServerPart started");
    }
}
