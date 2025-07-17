package cms.content.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "content_block_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentBlockHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_block_id", nullable = false)
    private ContentBlock contentBlock;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String type;

    @Lob
    private String content;

    @Column(name = "file_id")
    private Long fileId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_ip")
    private String createdIp;

    @Builder
    public ContentBlockHistory(ContentBlock contentBlock, int version, String type, String content, Long fileId,
            String createdBy, String createdIp) {
        this.contentBlock = contentBlock;
        this.version = version;
        this.type = type;
        this.content = content;
        this.fileId = fileId;
        this.createdBy = createdBy;
        this.createdIp = createdIp;
    }
}