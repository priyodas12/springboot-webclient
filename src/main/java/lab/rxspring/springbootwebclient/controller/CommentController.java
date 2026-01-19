package lab.rxspring.springbootwebclient.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lab.rxspring.springbootwebclient.model.Comment;
import lab.rxspring.springbootwebclient.service.CommentService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequestMapping("/api/v1/external/comments")
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/{id}")
    public Mono<Comment> getCommentById(@PathVariable String id) {
        log.info("get external comment by id {}", id);
        return commentService.getExternalCommentById(id);
    }
}
