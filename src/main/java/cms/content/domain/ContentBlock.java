package cms.content.domain;

import cms.file.entity.CmsFile;
import cms.menu.domain.Menu;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "content_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class) // Auditing 활성화
public class ContentBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Column(nullable = false)
    private String type;

    @Lob
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private CmsFile file;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private int version = 1;

    @OneToMany(mappedBy = "contentBlock", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version DESC")
    private List<ContentBlockHistory> history = new ArrayList<>();

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
    public ContentBlock(Menu menu, String type, String content, CmsFile file, int sortOrder, String createdBy,
            String createdIp) {
        this.menu = menu;
        this.type = type;
        this.content = content;
        this.file = file;
        this.sortOrder = sortOrder;
        this.createdBy = createdBy;
        this.createdIp = createdIp;
        this.updatedBy = createdBy;
        this.updatedIp = createdIp;
    }

    public void update(String type, String content, CmsFile file, String updatedBy, String updatedIp) {
        this.type = type;
        this.content = content;
        this.file = file;
        this.updatedBy = updatedBy;
        this.updatedIp = updatedIp;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void increaseVersion() {
        this.version++;
    }

    public void restore(ContentBlockHistory historyEntry, CmsFile file, String updatedBy, String updatedIp) {
        this.type = historyEntry.getType();
        this.content = historyEntry.getContent();
        this.file = file;
        this.updatedBy = updatedBy;
        this.updatedIp = updatedIp;
    }
}