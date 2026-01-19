package lab.rxspring.springbootwebclient.dao;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import lab.rxspring.springbootwebclient.model.Comment;
import reactor.core.publisher.Mono;

@Repository
public interface CommentDao extends ReactiveCrudRepository<Comment, Long> {

    @Query("""
            INSERT INTO comments (
                post_id,
                external_comment_id,
                name,
                email,
                body,
                created_at,
                updated_at
            )
            VALUES (
                :postId,
                :externalCommentId,
                :name,
                :email,
                :body,
                NOW(),
                NOW()
            )
            ON CONFLICT (external_comment_id)
            DO UPDATE SET
                post_id = EXCLUDED.post_id,
                name = EXCLUDED.name,
                email = EXCLUDED.email,
                body = EXCLUDED.body,
                updated_at = NOW()
            RETURNING
                    id,
                    post_id,
                    external_comment_id,
                    name,
                    email,
                    body,
                    created_at,
                    updated_at
            """)
    Mono<Comment> upsert(
            Long postId,
            Long externalCommentId,
            String name,
            String email,
            String body
    );
}
