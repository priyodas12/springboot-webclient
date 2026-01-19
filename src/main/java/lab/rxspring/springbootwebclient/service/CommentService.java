package lab.rxspring.springbootwebclient.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.netty.handler.timeout.TimeoutException;
import lab.rxspring.springbootwebclient.dao.CommentDao;
import lab.rxspring.springbootwebclient.model.Comment;
import lab.rxspring.springbootwebclient.model.CommentDto;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

@Slf4j
@Service
public class CommentService {

    private final CommentDao commentDao;
    private final WebClient webClient;

    public CommentService(CommentDao commentDao, WebClient webClient) {
        this.commentDao = commentDao;
        this.webClient = webClient;
    }

    // Fetches external comment by ID, maps to entity, saves to database
    public Mono<Comment> getExternalCommentById(String id) {
        return getCommentById(id)  // Fetch comment from external API
                .flatMap(this::mapToComment)  // Transform DTO to entity
                .flatMap(data -> saveOrUpdateCommentData(id, data))  // Persist to database
                .flatMap(Mono::just)  // Wrap result in Mono (redundant, can be removed)
                .onErrorResume(err -> {  // Handle any errors from the entire chain
                    log.info("Exception:getExternalCommentById: {}, Exception: {}", id, ExceptionUtils.getRootCauseMessage(err));
                    return Mono.error(new RuntimeException("Error:Failed to save comment to database", err));
                });
    }

    // Fetches comment from external API with retry and timeout handling
    private Mono<CommentDto> getCommentById(String id) {
        return webClient.get()  // Initiate HTTP GET request
                .uri("/{id}", id)  // Set URI with path variable
                .accept(MediaType.APPLICATION_JSON)  // Set Accept header to JSON
                .retrieve()  // Execute request and retrieve response
                /*.onRawStatus(code -> code > 399, response -> {  // Alternative raw status code handling (commented)
                    log.info("onRawStatus:getCommentById: {}, response: {}", id, response.statusCode());
                    return Mono.error(new RuntimeException("onRawStatus:getCommentById: Failed to get comment"));
                })*/
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> handle4xxClientError(clientResponse, id))  // Handle 4xx client errors
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> handle5xxClientError(clientResponse, id))  // Handle 5xx server errors
                .bodyToMono(CommentDto.class)  // Deserialize response body to CommentDto
                .doOnNext(comment -> {  // Log successful retrieval
                    log.info("getCommentById: {}, comment: {}", id, comment);
                })
                .timeout(Duration.ofSeconds(3))  // Set 3-second timeout for the request
                .retryWhen(getRetryBackoffSpec(id))  // Apply exponential backoff retry strategy
                .flatMap(Mono::just)  // Wrap in Mono (redundant, can be removed)
                .onErrorResume(WebClientResponseException.class, e -> {  // Handle HTTP response errors (4xx, 5xx)
                    log.info("WebClientResponseException: getCommentById: {}, Exception: {}", id, ExceptionUtils.getRootCauseMessage(e));
                    return Mono.empty();
                })
                .onErrorResume(WebClientRequestException.class, e -> {  // Handle connection/request errors (DNS, connection refused)
                    log.info("WebClientRequestException: getCommentById: {}, Exception: {}", id, ExceptionUtils.getRootCauseMessage(e));
                    return Mono.empty();
                })
                .onErrorResume(TimeoutException.class, e -> {  // Handle timeout errors after retry exhaustion
                    log.info("TimeoutException: getCommentById: {}, Exception: {}", id, ExceptionUtils.getRootCauseMessage(e));
                    return Mono.empty();
                })
                .onErrorResume(throwable -> {  // Catch-all for any unexpected errors
                    log.info("Exception: getCommentById: {}, Exception: {}", id, ExceptionUtils.getRootCauseMessage(throwable));
                    return Mono.empty();
                });
    }

    // Maps CommentDto from API to Comment entity for database persistence
    private Mono<Comment> mapToComment(CommentDto data) {
        return Mono.just(Comment.builder()  // Build Comment entity using builder pattern
                        .name(data.getName())  // Map name field
                        .body(data.getBody())  // Map body field
                        .email(data.getEmail())  // Map email field
                        .externalCommentId(data.getId())  // Map external API ID
                        .postId(data.getPostId())  // Map post ID
                        .createdAt(Timestamp.from(Instant.now()))  // Set creation timestamp
                        .updatedAt(Timestamp.from(Instant.now()))  // Set update timestamp
                        .build())  // Build the entity
                .onErrorResume(throwable -> {  // Handle mapping errors (unlikely but defensive)
                    log.info("mapToComment: {}, Exception: {}", data, ExceptionUtils.getRootCauseMessage(throwable));
                    return Mono.empty();  // Return empty on error
                });
    }

    // Saves or updates comment data in database with duplicate key handling
    private @NonNull Mono<Comment> saveOrUpdateCommentData(String id, Comment data) {
        return commentDao.upsert(data.getPostId(), data.getExternalCommentId(), data.getName(), data.getEmail(), data.getBody())  // Execute upsert operation
                .doOnSuccess(comment -> {  // Log successful save operation
                    log.info("Successfully saved comment {}", comment);
                })
                .onErrorResume(DuplicateKeyException.class, ex -> {  // Handle duplicate key constraint violations
                    log.error("DuplicateKeyException:getExternalCommentById: Database error saving comment for ID: {}, error: {}",
                            id, ex.getMessage(), ex);
                    return Mono.error(new RuntimeException("DuplicateKeyException:Failed to save comment to database", ex));
                })
                .onErrorResume(DataAccessException.class, ex -> {  // Handle general database access errors
                    log.error("DataAccessException:getExternalCommentById: Database error saving comment for ID: {}, error: {}",
                            id, ex.getMessage(), ex);
                    return Mono.error(new RuntimeException("DataAccessException:Failed to save comment to database", ex));
                })
                .onErrorResume(throwable -> {  // Catch-all for any unexpected errors
                    log.error("{}:getExternalCommentById: Database error saving comment for ID: {}", throwable.getCause(), id);
                    return Mono.error(new RuntimeException("Exception:Failed to save comment to database", throwable));
                });
    }


    // Configures exponential backoff retry strategy for API calls
    private @NonNull RetryBackoffSpec getRetryBackoffSpec(String id) {
        return Retry
                .backoff(3, Duration.ofMillis(500))  // Retry up to 3 times with 500ms initial delay
                .maxBackoff(Duration.ofSeconds(2))  // Cap maximum delay at 2 seconds
                .filter(throwable -> throwable instanceof WebClientRequestException || throwable instanceof WebClientResponseException || throwable instanceof TimeoutException)  // Only retry on specific transient errors
                .doBeforeRetry(retrySignal -> log.info("BeforeRetry:: commentId: {}, attempts: {}, retry failure cause: {}", id, retrySignal.totalRetries() + 1, retrySignal.failure().getMessage()))  // Log before each retry attempt
                .doAfterRetry(retrySignal -> log.info("AfterRetry:commentId: {}, attempts: {}, retry failure cause: {}", id, retrySignal.totalRetries() + 1, retrySignal.failure().getMessage()))  // Log after each retry attempt
                .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) -> {  // Handle retry exhaustion after all attempts fail
                    log.error("Retry Exhausted for commentId: {}, attempts: {}", id, retrySignal.totalRetries() + 1);
                    return retrySignal.failure();  // Propagate original failure exception
                }));
    }
    
    // Handles 4xx client errors from API responses
    private Mono<? extends Throwable> handle4xxClientError(ClientResponse response, String id) {
        return response.bodyToMono(String.class)  // Extract error response body as String
                .flatMap(err -> {  // Process error body
                    log.error("Client Error: status {}", response.statusCode());  // Log client error with status
                    return Mono.error(new WebClientResponseException(  // Create custom exception with context
                            "Client error retrieving comment with commentId: " + id,  // Error message
                            response.statusCode().value(),  // HTTP status code
                            response.statusCode().toString(),  // Status text
                            null,  // Headers (null for simplicity)
                            err.getBytes(),  // Error response body
                            null));  // Charset (null for default)
                });
    }

    // Handles 5xx server errors from API responses
    private Mono<? extends Throwable> handle5xxClientError(ClientResponse response, String id) {
        return response.bodyToMono(String.class)  // Extract error response body as String
                .flatMap(err -> {  // Process error body
                    log.error("Server Error: status {}", response.statusCode());  // Log server error with status
                    return Mono.error(new WebClientResponseException(  // Create custom exception with context
                            "Server error retrieving comment with commentId: " + id,  // Error message
                            response.statusCode().value(),  // HTTP status code
                            response.statusCode().toString(),  // Status text
                            null,  // Headers (null for simplicity)
                            err.getBytes(),  // Error response body
                            null));  // Charset (null for default)
                });
    }
}

