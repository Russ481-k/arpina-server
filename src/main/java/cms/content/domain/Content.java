package cms.content.domain;

import cms.template.domain.Template;
import cms.user.domain.User;
import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "CONTENT")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Content {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONTENT_ID")
    private Long id;

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;

    @Column(name = "DESCRIPTION", columnDefinition = "TEXT")
    private String description;

    @Column(name = "CONTENT", columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TEMPLATE_ID", nullable = false, referencedColumnName = "TEMPLATE_ID")
    private Template template;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ContentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CREATOR_ID", nullable = false, referencedColumnName = "uuid")
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UPDATER_ID", referencedColumnName = "uuid")
    private User updater;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    @Column(name = "EXPIRED_AT")
    private LocalDateTime expiredAt;

    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "IS_DELETED", nullable = false)
    private boolean isDeleted;

    // Template Content fields
    @Enumerated(EnumType.STRING)
    @Column(name = "WIDGET_TYPE")
    private WidgetType widgetType;

    @Column(name = "DATA_JSON", columnDefinition = "JSON")
    private String dataJson;

    @PrePersist
    public void prePersist() {
        this.status = ContentStatus.DRAFT;
        this.isDeleted = false;
    }

    public static Content createContent(String title, String content, Template template, User creator) {
        return Content.builder()
                .title(title)
                .content(content)
                .template(template)
                .creator(creator)
                .status(ContentStatus.DRAFT)
                .isDeleted(false)
                .build();
    }

    public void updateStatus(ContentStatus status) {
        this.status = status;
    }

    public void updateData(String dataJson) {
        this.dataJson = dataJson;
    }
} 