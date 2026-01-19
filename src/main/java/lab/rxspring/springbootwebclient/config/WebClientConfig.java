package lab.rxspring.springbootwebclient.config;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.*;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl("https://jsonplaceholder.typicode.com/comments/")

                // Set default headers for all requests
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "MyApp/1.0")

                // Set connection timeout (how long to wait for connection establishment)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                                .responseTimeout(Duration.ofSeconds(10))  // Response timeout
                                .doOnConnected(conn ->
                                        conn.addHandlerLast(new ReadTimeoutHandler(10))  // Read timeout
                                                .addHandlerLast(new WriteTimeoutHandler(10))  // Write timeout
                                )
                ))

                // Configure connection pool settings
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().baseUrl("https://jsonplaceholder.typicode.com/comments/")
                ))

                // Set max in-memory buffer size for request/response (default 256KB)
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))  // 16MB buffer

                // Add request/response logging filter for debugging
                .filter(ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
                    log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
                    return Mono.just(clientRequest);
                }))

                // Add response logging filter
                .filter(ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
                    log.info("Response: {}", clientResponse.statusCode());
                    return Mono.just(clientResponse);
                }))

                // Add retry filter (alternative to retryWhen in method)
                .filter((request, next) ->
                        next.exchange(request)
                                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                                        .filter(throwable -> throwable instanceof WebClientRequestException)
                                )
                )

                // Add global error handler filter
                .filter(ExchangeFilterFunction.ofResponseProcessor(response -> {
                    if (response.statusCode().isError()) {
                        log.error("Error response: {}", response.statusCode());
                    }
                    return Mono.just(response);
                }))

                // Add authentication header filter (if needed)
                .filter((request, next) -> {
                    ClientRequest filtered = ClientRequest.from(request)
                            .header("Authorization", "Bearer token-here")
                            .build();
                    return next.exchange(filtered);
                })

                // Add custom headers based on request
                .defaultRequest(spec -> spec
                        .header("X-Request-ID", UUID.randomUUID().toString())
                )

                // Set default URI variables
                .defaultUriVariables(Map.of("version", "v1"))

                // Add request size limit filter
                .filter((request, next) -> {
                    long contentLength = request.headers().getContentLength();
                    if (contentLength > 10 * 1024 * 1024) {  // 10MB limit
                        return Mono.error(new IllegalStateException("Request too large"));
                    }
                    return next.exchange(request);
                })

                // Configure exchange strategies for custom codecs
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> {
                            configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024);
                            // Add custom encoders/decoders here if needed
                        })
                        .build())

                // Clone configuration for modification (useful for testing)
                .clone()

                .build();
    }
}
