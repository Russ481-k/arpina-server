package cms.menu.domain;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import cms.user.domain.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import cms.content.domain.ContentBlock;

@Entity
@Table(name = "menu", uniqueConstraints = {
        @UniqueConstraint(columnNames = "name"),
        @UniqueConstraint(columnNames = "url")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('LINK','FOLDER','BOARD','CONTENT','PROGRAM')")
    private MenuType type;

    @Column(length = 255)
    private String url;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "display_position", nullable = false, length = 50)
    private String displayPosition;

    @Column(name = "visible")
    private Boolean visible;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "parent_id")
    private Long parentId;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @Builder.Default
    private List<Menu> children = new ArrayList<>();

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ContentBlock> contentBlocks = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "uuid")
    private User createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "uuid")
    private User updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void update(String name, MenuType type, String url, Long targetId,
            String displayPosition, Boolean visible, Integer sortOrder,
            Long parentId) {
        if (name != null)
            this.name = name;
        if (type != null)
            this.type = type;
        if (url != null)
            this.url = url;
        if (targetId != null)
            this.targetId = targetId;
        if (displayPosition != null)
            this.displayPosition = displayPosition;
        if (visible != null)
            this.visible = visible;
        if (sortOrder != null)
            this.sortOrder = sortOrder;
        if (parentId != null)
            this.parentId = parentId;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void addChild(Menu child) {
        children.add(child);
        child.setParentId(this.id);
    }

    public void removeChild(Menu child) {
        children.remove(child);
        child.setParentId(null);
    }

    public void updateTargetId(Long targetId) {
        this.targetId = targetId;
    }
}