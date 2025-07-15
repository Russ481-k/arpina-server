package cms.board.domain;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bbs_comment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BbsCommentDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMMENT_ID")
    private Long commentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "NTT_ID", nullable = false)
    private BbsArticleDomain article;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "WRITER", nullable = false, length = 50)
    private String writer;

    @Column(name = "DISPLAY_WRITER", length = 50)
    private String displayWriter;

    @Column(name = "IS_DELETED", nullable = false, length = 1)
    private String isDeleted = "N";

    @Column(name = "CREATED_BY", length = 36)
    private String createdBy;

    @Column(name = "CREATED_IP", length = 45)
    private String createdIp;

    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY", length = 36)
    private String updatedBy;

    @Column(name = "UPDATED_IP", length = 45)
    private String updatedIp;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}