package cms.board.domain;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bbs_category")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbsCategoryDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bbs_id", nullable = false)
    private BbsMasterDomain bbsMaster;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 1)
    private String displayYn;

    @Column(length = 36)
    private String createdBy;

    @Column(length = 45)
    private String createdIp;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(length = 36)
    private String updatedBy;

    @Column(length = 45)
    private String updatedIp;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void update(String code, String name, int sortOrder, String displayYn) {
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
        this.displayYn = displayYn;
    }
} 