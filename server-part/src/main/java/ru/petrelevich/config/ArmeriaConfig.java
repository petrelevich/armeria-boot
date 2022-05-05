package ru.petrelevich.config;


import com.linecorp.armeria.server.ServerErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringValueResolver;
import ru.petrelevich.armeria.RequestMapping;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Configuration
@Slf4j
public class ArmeriaConfig {
    private static final String SPRING_PLACEHOLDER = "(\\$\\{.*})";
    private final Pattern springPlaceHolderPattern;

    private final ApplicationContext context;
    private final StringValueResolver stringValueResolver;
    private final int port;
    private final int threadNumber;

    public ArmeriaConfig(ApplicationContext context,
                         ConfigurableBeanFactory beanFactory,
                         @Value("${server.port}") int port,
                         @Value("${server.thread-number}") int threadNumber) {
        this.context = context;
        this.stringValueResolver = new EmbeddedValueResolver(beanFactory);
        this.springPlaceHolderPattern = Pattern.compile(SPRING_PLACEHOLDER, Pattern.MULTILINE);
        this.port = port;
        this.threadNumber = threadNumber;
    }

    @Bean
    public ArmeriaServer server(ServerErrorHandler errorHandler) {
        var services = context.getBeansWithAnnotation(RequestMapping.class);
        if (services.isEmpty()) {
            throw new IllegalArgumentException("Beans with @RequestMapping not found");
        }
        Map<String, Object> pathService = new HashMap<>();
        for (var serviceEntry: services.entrySet()) {
            var service = serviceEntry.getValue();
            var path = resolvePath(service.getClass().getAnnotation(RequestMapping.class).value());
            pathService.put(path, service);
        }
        return new ArmeriaServer(port, threadNumber, errorHandler, pathService);
    }

    private String resolvePath(String value) {
        Matcher matcher = springPlaceHolderPattern.matcher(value);

        if (matcher.find()) {
            var placeHolder = matcher.group(0);
            var propValue = stringValueResolver.resolveStringValue(placeHolder);
            if (propValue == null) {
                throw new IllegalArgumentException("can't find value for \"" + placeHolder + "\"");
            }
            return value.replaceFirst(SPRING_PLACEHOLDER, propValue);
        }
        return value;
    }
}
