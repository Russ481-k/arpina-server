package cms.template.domain;

import cms.content.domain.Content;
import cms.template.exception.CannotDeleteFixedTemplateException;
import lombok.*;
import org.hibernate.annotations.Where;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "TEMPLATE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Where(clause = "DELETED_YN = false")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TEMPLATE_ID")
    private Long templateId;

    @Column(name = "TEMPLATE_NM", nullable = false, length = 100)
    private String templateName;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "TYPE", length = 10, nullable = false)
    @Enumerated(EnumType.STRING)
    private TemplateType type;

    @Column(name = "LAYOUT_JSON", nullable = false, columnDefinition = "JSON")
    private String layoutJson;

    @Column(name = "IS_PUBLISHED", nullable = false)
    private boolean published = false;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Content> contents = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TemplateVersion> versions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "DELETED_YN", nullable = false)
    private boolean deleted = false;

    @Column(name = "VERSION_NO", nullable = false)
    private int versionNo = 1;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TemplateRow> rows = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Builder
    public Template(String templateName, String description, String layoutJson, boolean published, int versionNo, List<TemplateRow> rows) {
        this.templateName = templateName;
        this.description = description;
        this.layoutJson = layoutJson;
        this.published = published;
        this.versionNo = versionNo;
        this.rows = rows != null ? rows : new ArrayList<>();
    }

    public void update(String templateName, String description, String layoutJson) {
        this.templateName = templateName;
        this.description = description;
        this.layoutJson = layoutJson;
    }

    public void publish() {
        this.published = true;
    }

    public void unpublish() {
        this.published = false;
    }

    public static Template createTemplate(String templateName, String description) {
        Template template = new Template();
        template.templateName = templateName;
        template.description = description;
        template.type = TemplateType.SUB;
        template.layoutJson = "{}";
        template.published = false;
        return template;
    }

    public void delete() {
        if (this.type == TemplateType.MAIN || this.type == TemplateType.SUB) {
            throw new CannotDeleteFixedTemplateException();
        }
        this.deleted = true;
    }

    public void addVersion(String version, String comment) {
        TemplateVersion templateVersion = TemplateVersion.createVersion(this, version, comment);
        this.versions.add(templateVersion);
    }

    public void setType(TemplateType type) {
        this.type = type;
    }

    public void addRow(TemplateRow row) {
        this.rows.add(row);
        row.setTemplate(this);
    }

    public void updateLayout(String layoutJson) {
        this.layoutJson = layoutJson;
    }

    @Builder
    public static class TemplateBuilder {
        private Long templateId;
        private String templateName;
        private String description;
        private TemplateType type;
        private boolean published;
        private int versionNo;
        private List<TemplateRow> rows;
        private String layoutJson;

        public TemplateBuilder() {
            this.rows = new ArrayList<>();
            this.published = false;
            this.versionNo = 1;
        }

        public TemplateBuilder(String templateName, String description, TemplateType type, boolean published, int versionNo, List<TemplateRow> rows, String layoutJson) {
            this.templateName = templateName;
            this.description = description;
            this.type = type;
            this.published = published;
            this.versionNo = versionNo;
            this.rows = rows != null ? rows : new ArrayList<>();
            this.layoutJson = layoutJson;
        }

        public TemplateBuilder(Long templateId, String templateName, String description, TemplateType type, boolean published, int versionNo, List<TemplateRow> rows, String layoutJson) {
            this.templateId = templateId;
            this.templateName = templateName;
            this.description = description;
            this.type = type;
            this.published = published;
            this.versionNo = versionNo;
            this.rows = rows != null ? rows : new ArrayList<>();
            this.layoutJson = layoutJson;
        }

        public TemplateBuilder type(TemplateType type) {
            this.type = type;
            return this;
        }

        public TemplateBuilder published(boolean published) {
            this.published = published;
            return this;
        }

        public TemplateBuilder versionNo(int versionNo) {
            this.versionNo = versionNo;
            return this;
        }

        public TemplateBuilder addRow(TemplateRow row) {
            if (this.rows == null) {
                this.rows = new ArrayList<>();
            }
            this.rows.add(row);
            return this;
        }

        public TemplateBuilder templateId(Long templateId) {
            this.templateId = templateId;
            return this;
        }

        public TemplateBuilder layoutJson(String layoutJson) {
            this.layoutJson = layoutJson;
            return this;
        }

        public Template build() {
            return new Template(templateId, templateName, description, type, published, versionNo, rows, layoutJson);
        }
    }

    @Builder
    public Template(Long templateId, String templateName, String description, TemplateType type, boolean published, int versionNo, List<TemplateRow> rows, String layoutJson) {
        this.templateId = templateId;
        this.templateName = templateName;
        this.description = description;
        this.type = type;
        this.published = published;
        this.versionNo = versionNo;
        this.rows = rows != null ? rows : new ArrayList<>();
        this.layoutJson = layoutJson;
    }
} 
 
 
 