package cms.popup.domain;

import javax.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "popup")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Popup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_visible")
    private boolean isVisible = true;

    @Column(name = "display_order")
    private Integer displayOrder = 1;

    @Column(name = "CREATED_BY", length = 36)
    private String createdBy;

    @Column(name = "CREATED_IP", length = 45)
    private String createdIp;

    @CreationTimestamp
    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY", length = 36)
    private String updatedBy;

    @Column(name = "UPDATED_IP", length = 45)
    private String updatedIp;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    public void setContent(String content) {
        this.content = content;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Builder
    public Popup(String title, String content, LocalDateTime startDate, LocalDateTime endDate, boolean isVisible,
            Integer displayOrder, String createdBy, String createdIp, String updatedBy, String updatedIp) {
        this.title = title;
        this.content = content;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isVisible = isVisible;
        this.displayOrder = displayOrder;
        this.createdBy = createdBy;
        this.createdIp = createdIp;
        this.updatedBy = updatedBy;
        this.updatedIp = updatedIp;
    }

    public void update(String title, String content, LocalDateTime startDate, LocalDateTime endDate, boolean isVisible,
            Integer displayOrder) {
        this.title = title;
        this.content = content;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isVisible = isVisible;
        this.displayOrder = displayOrder;
    }

    public void updateVisibility(boolean isVisible) {
        this.isVisible = isVisible;
    }

    public void updateDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}