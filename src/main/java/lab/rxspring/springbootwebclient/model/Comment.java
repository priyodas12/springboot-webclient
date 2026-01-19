package lab.rxspring.springbootwebclient.model;

import java.sql.Timestamp;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Table(name = "comments")
public class Comment {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    private Long id;
    private Long externalCommentId;
    private long postId;
    private String name;
    private String email;
    private String body;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    @Override
    public String toString() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "Comment{errorSerializing=true}";
        }
    }

}
