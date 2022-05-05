package ru.petrelevich.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.internal.common.JacksonUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return JacksonUtil.newDefaultObjectMapper();
    }
}
