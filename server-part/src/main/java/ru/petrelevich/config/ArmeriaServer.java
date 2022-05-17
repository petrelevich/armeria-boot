package ru.petrelevich.config;

import com.linecorp.armeria.common.Flags;
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats;
import com.linecorp.armeria.common.util.TransportType;
import com.linecorp.armeria.server.HttpServiceWithRoutes;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServerErrorHandler;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.docs.DocServiceFilter;
import com.linecorp.armeria.server.grpc.GrpcService;
import example.armeria.grpc.reactor.Hello;
import example.armeria.grpc.reactor.HelloServiceGrpc;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.netty.channel.EventLoopGroup;
import lombok.NonNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import reactor.HelloServiceImpl;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


public class ArmeriaServer implements InitializingBean, DisposableBean {
    private final Server server;
    private static final String THREAD_NAME_TEMPLATE = "Main-loop-%s";
    private static final String BLOCKED_THREAD_NAME_TEMPLATE = "Blocked-loop-%s";

    public ArmeriaServer(int port,
                         int threadNumber,
                         int threadNumberBlocked,
                         int requestTimeoutSec,
                         ServerErrorHandler errorHandler,
                         Map<String, Object> pathServices) {
        var sb = Server.builder();
        sb.http(port);
        sb.workerGroup(makeEventLoopGroup(threadNumber, THREAD_NAME_TEMPLATE), true);
        sb.blockingTaskExecutor(makeEventLoopGroup(threadNumberBlocked, BLOCKED_THREAD_NAME_TEMPLATE), true);
        sb.errorHandler(errorHandler);
        sb.requestTimeout(Duration.ofSeconds(requestTimeoutSec));
        annotatedServices(sb, pathServices);
        grpcServices(sb);
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

    private EventLoopGroup makeEventLoopGroup(int nThreads, String nameTemplate) {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadCounter = new AtomicInteger(0);

            @Override
            public Thread newThread(@NonNull Runnable run) {
                var thread = new Thread(run);
                thread.setDaemon(true);
                thread.setName(String.format(nameTemplate, threadCounter.incrementAndGet()));
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

    private void grpcServices(ServerBuilder sb) {
        final Hello.HelloRequest exampleRequest = Hello.HelloRequest.newBuilder().setName("Armeria").build();
        final HttpServiceWithRoutes grpcService =
                GrpcService.builder()
                        .addService(new HelloServiceImpl())
                        // See https://github.com/grpc/grpc-java/blob/master/documentation/server-reflection-tutorial.md
                        .addService(ProtoReflectionService.newInstance())
                        .supportedSerializationFormats(GrpcSerializationFormats.values())
                        .enableUnframedRequests(true)
                        // You can set useBlockingTaskExecutor(true) in order to execute all gRPC
                        // methods in the blockingTaskExecutor thread pool.
                        // .useBlockingTaskExecutor(true)
                        .build();
        sb.service(grpcService)
                // You can access the documentation service at http://127.0.0.1:8080/docs.
                // See https://armeria.dev/docs/server-docservice for more information.
                .serviceUnder("/docs",
                        DocService.builder()
                                .exampleRequests(
                                        HelloServiceGrpc.SERVICE_NAME,
                                        "Hello", exampleRequest)
                                .exampleRequests(
                                        HelloServiceGrpc.SERVICE_NAME,
                                        "LazyHello", exampleRequest)
                                .exampleRequests(
                                        HelloServiceGrpc.SERVICE_NAME,
                                        "BlockingHello", exampleRequest)
                                .exclude(DocServiceFilter.ofServiceName(
                                        ServerReflectionGrpc.SERVICE_NAME))
                                .build());
    }

}
