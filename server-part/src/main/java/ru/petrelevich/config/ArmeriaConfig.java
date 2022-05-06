package ru.petrelevich.config;


import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServerErrorHandler;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.matcher.ElementMatchers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringValueResolver;
import ru.petrelevich.armeria.GetAnnotation;
import ru.petrelevich.armeria.GetMapping;
import ru.petrelevich.armeria.InvocationHandlerArmeria;
import ru.petrelevich.armeria.ParamAnnotaion;
import ru.petrelevich.armeria.PathVariable;
import ru.petrelevich.armeria.RequestMapping;
import ru.petrelevich.armeria.RequestParam;
import ru.petrelevich.services.IndexService;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.bytebuddy.matcher.ElementMatchers.named;


@Configuration
@Slf4j
public class ArmeriaConfig {
    private static final String SPRING_PLACEHOLDER = "(\\$\\{.*})";
    private final Pattern springPlaceHolderPattern;

    private final ApplicationContext context;
    private final StringValueResolver stringValueResolver;
    private final int port;
    private final int threadNumber;
    private final int threadNumberBlocked;
    private final int requestTimeoutSec;
    private long methodCounter = 0;

    public ArmeriaConfig(ApplicationContext context,
                         ConfigurableBeanFactory beanFactory,
                         @Value("${server.port}") int port,
                         @Value("${server.thread-number}") int threadNumber,
                         @Value("${server.thread-number-blocked}") int threadNumberBlocked,
                         @Value("${server.request-timeout-sec}") int requestTimeoutSec) {
        this.context = context;
        this.stringValueResolver = new EmbeddedValueResolver(beanFactory);
        this.springPlaceHolderPattern = Pattern.compile(SPRING_PLACEHOLDER, Pattern.MULTILINE);
        this.port = port;
        this.threadNumber = threadNumber;
        this.threadNumberBlocked = threadNumberBlocked;
        this.requestTimeoutSec = requestTimeoutSec;
    }

    @Bean
    public ArmeriaServer server(ServerErrorHandler errorHandler) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Map<String, Object> pathService = makeArmeriaBeans();
        return new ArmeriaServer(port, threadNumber, threadNumberBlocked, requestTimeoutSec, errorHandler, pathService);
    }

    private Map<String, Object> makeArmeriaBeans() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        var services = context.getBeansWithAnnotation(RequestMapping.class);
        if (services.isEmpty()) {
            throw new IllegalArgumentException("Beans with @RequestMapping not found");
        }
        Map<String, Object> pathService = new HashMap<>();
        for (var serviceEntry : services.entrySet()) {
            var service = serviceEntry.getValue();
            var path = resolvePath(service.getClass().getAnnotation(RequestMapping.class).value());
            pathService.put(path, makeArmeriaProxy(service));
        }
        return pathService;
    }

    private Object makeArmeriaProxy(Object object) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        /*
        Class<?> dynamicType = new ByteBuddy()
                .rebase(object.getClass())
                .defineMethod("processBB", HttpResponse.class, Visibility.PUBLIC)
                .withParameters(String.class,  int.class, String.class)
                .intercept(
                        MethodCall.invoke(HttpResponse.class.getMethod("ofJson", Object.class))
                                .withMethodCall(MethodCall.invoke(named("process")).withAllArguments())
                )
                .annotateMethod(new GetAnnotation("/param/{name}/{id}"))
                .annotateParameter(0, new ParamAnnotaion("name"))
                .annotateParameter(1, new ParamAnnotaion("id"))
                .annotateParameter(2, new ParamAnnotaion("gender"))
                .make()
                .load(object.getClass().getClassLoader(), ClassLoadingStrategy.Default.CHILD_FIRST)
                .getLoaded();

        var method = dynamicType.getMethod("process", String.class,  int.class, String.class);
        for (var ann: method.getDeclaredAnnotations()) {
            System.out.println(ann);
        }
*/
        Map<Method, String> methodPath = new HashMap<>();
        Map<Method, List<String>> methodParams = new HashMap<>();
        for (var method : object.getClass().getMethods()) {
            var getMapping = method.getAnnotation(GetMapping.class);
            if (getMapping != null) {
                methodPath.put(method, getMapping.value());
                for (var parameter : method.getParameters()) {
                    var pathVar = parameter.getAnnotation(PathVariable.class);
                    if (pathVar != null) {
                        methodParams.compute(method, (k, v) -> {
                            var list = (v == null) ? new ArrayList<String>() : v;
                            list.add(pathVar.value());
                            return list;
                        });
                    }
                    var requestPar = parameter.getAnnotation(RequestParam.class);
                    if (requestPar != null) {
                        methodParams.compute(method, (k, v) -> {
                            var list = (v == null) ? new ArrayList<String>() : v;
                            list.add(requestPar.value());
                            return list;
                        });
                    }
                }

            }
        }
        var proxyClass = makeProxy(object.getClass(), methodPath,  methodParams);
        return proxyClass.getDeclaredConstructor().newInstance();
    }

    private Class<?> makeProxy(Class<?> baseClass, Map<Method, String> methodPath, Map<Method, List<String>> methodParams) throws NoSuchMethodException {
        var builder = new ByteBuddy().rebase(baseClass);
        DynamicType.Builder.MethodDefinition<?> dynamicTypeBuilder = null;
        for (var methodEntry : methodPath.entrySet()) {
            var method = methodEntry.getKey();
            var path = methodEntry.getValue();

            dynamicTypeBuilder = builder.defineMethod(makeMethodName(method.getName()), HttpResponse.class, Visibility.PUBLIC)
                    .withParameters(method.getParameterTypes())
                    .intercept(
                            MethodCall.invoke(HttpResponse.class.getMethod("ofJson", Object.class))
                                    .withMethodCall(MethodCall.invoke(method).withAllArguments())
                    )
                    .annotateMethod(new GetAnnotation(path));

            var paramList = methodParams.get(method);
            for (var idx = 0; idx < paramList.size(); idx++) {
                dynamicTypeBuilder = dynamicTypeBuilder.annotateParameter(idx, new ParamAnnotaion(paramList.get(idx)));
            }
        }

        if (dynamicTypeBuilder == null) {
            throw new IllegalArgumentException("methods for proxy not found");
        }

        return dynamicTypeBuilder.make()
                .load(baseClass.getClassLoader(), ClassLoadingStrategy.Default.CHILD_FIRST)
                .getLoaded();
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

    private String makeMethodName(String name) {
        return String.format("%s$bb%d", name, ++methodCounter);
    }
}
