package ru.petrelevich.config;

import com.linecorp.armeria.common.Flags;
import com.linecorp.armeria.common.util.TransportType;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServerErrorHandler;
import io.netty.channel.EventLoopGroup;
import lombok.NonNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


public class ArmeriaServer implements InitializingBean, DisposableBean {
    private final Server server;
    private static final String THREAD_NAME_TEMPLATE = "Main-loop-thread-%s";

    public ArmeriaServer(int port,
                         int threadNumber,
                         ServerErrorHandler errorHandler,
                         Map<String, Object> pathServices) {
        var sb = Server.builder();
        sb.http(port);
        sb.workerGroup(makeEventLoopGroup(threadNumber), true);
        sb.errorHandler(errorHandler);
        annotatedServices(sb, pathServices);
        server = sb.build();
    }

    @Override
    public void afterPropertiesSet() {
        server.start().join();
    }

    @Override
    public void destroy() {
        server.stop().join();
    }

    private EventLoopGroup makeEventLoopGroup(int nThreads) {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadCounter = new AtomicInteger(0);

            @Override
            public Thread newThread(@NonNull Runnable run) {
                var thread = new Thread(run);
                thread.setDaemon(true);
                thread.setName(String.format(THREAD_NAME_TEMPLATE, threadCounter.incrementAndGet()));
                return thread;
            }
        };

        TransportType type = Flags.transportType();
        return type.newEventLoopGroup(nThreads, unused -> threadFactory);
    }

    private void annotatedServices(ServerBuilder sb, Map<String, Object> pathServices) {
        for (var pathService: pathServices.entrySet()) {
            sb.annotatedService(pathService.getKey(), pathService.getValue());
        }
    }
}
