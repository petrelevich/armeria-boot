package ru.petrelevich.services;

import com.linecorp.armeria.common.Cookie;
import com.linecorp.armeria.common.Cookies;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.LogLevel;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Header;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.decorator.LoggingDecorator;
import org.springframework.stereotype.Service;
import ru.petrelevich.armeria.RequestMapping;

import java.util.Arrays;
import java.util.List;
@LoggingDecorator(
        requestLogLevel = LogLevel.INFO,
        successfulResponseLogLevel = LogLevel.INFO
)
public class IndexService {

    private final ApplService applService;

    public IndexService(ApplService applService) {
        this.applService = applService;
    }

    @Get("/param/{name}/{id}")
    @Blocking
    public HttpResponse param(@Param String name,  /* from path variable */
                              @Param int id,       /* from path variable and converted into integer*/
                              @Param Gender gender /* from query string and converted into enum */) throws InterruptedException {
        Thread.sleep(10_000);
        return HttpResponse.ofJson(applService.process(name, id, gender.name()));
    }

    @Get("/header")
    public HttpResponse header(@Header String xArmeriaText,            /* no conversion */
                               @Header List<Integer> xArmeriaSequence, /* converted into integer */
                               Cookies cookies                         /* converted into Cookies object */) {
        return HttpResponse.ofJson(
                Arrays.asList(xArmeriaText,
                        xArmeriaSequence,
                        cookies.stream().map(Cookie::name)
                                .toList()));
    }

    /**
     * A sample {@link Enum} for the automatic conversion example. The elements have unique names in a
     * case-insensitive way, so they can be converted in a case-insensitive way.
     */
    public enum Gender {
        MALE,
        FEMALE
    }
}
