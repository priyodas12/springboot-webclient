package lab.rxspring.springbootwebclient.model;

import lombok.Data;

@Data
public class CommentDto {
    private long postId;
    private long id;
    private String name;
    private String email;
    private String body;
}
