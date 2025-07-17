package cms.content.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "content_block_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
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

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_ip")
    private String createdIp;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_ip")
    private String updatedIp;

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
        this.updatedBy = createdBy; // 생성 시에는 생성자와 동일하게 설정
        this.updatedIp = createdIp; // 생성 시에는 생성자와 동일하게 설정
    }
}