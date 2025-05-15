package cms.template.domain;

import javax.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "TEMPLATE_VERSION")
@Getter
@NoArgsConstructor
public class TemplateVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VERSION_ID")
    private Long versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TEMPLATE_ID", nullable = false)
    private Template template;

    @Column(name = "VERSION_NO", nullable = false)
    private Integer versionNo;

    @Column(name = "LAYOUT_JSON", nullable = false, columnDefinition = "JSON")
    private String layoutJson;

    @Column(name = "COMMENT", length = 500)
    private String comment;

    @Column(name = "UPDATER", nullable = false, length = 50)
    private String updater;

    @CreationTimestamp
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    private TemplateVersion(Template template, Integer versionNo, String layoutJson, String comment, String updater) {
        this.template = template;
        this.versionNo = versionNo;
        this.layoutJson = layoutJson;
        this.comment = comment;
        this.updater = updater;
    }

    public static TemplateVersion createVersion(Template template, String version, String comment) {
        return new TemplateVersion(template, Integer.parseInt(version), template.getLayoutJson(), comment, "system");
    }
} 