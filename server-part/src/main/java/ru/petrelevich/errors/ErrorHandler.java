package ru.petrelevich.errors;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.annotation.Nullable;
import com.linecorp.armeria.server.ServerErrorHandler;
import com.linecorp.armeria.server.ServiceRequestContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ErrorHandler implements ServerErrorHandler {

    @Override
    public @Nullable HttpResponse onServiceException(@NonNull ServiceRequestContext ctx,
                                                     @NonNull Throwable cause) {
        log.error("error:{}", cause.getMessage(), cause);
        return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
