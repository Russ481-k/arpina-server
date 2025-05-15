package cms.board.domain;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;

@Entity
@Table(name = "bbs_article", indexes = {
    @Index(name = "IDX_BBS_ARTICLE_BBS_ID", columnList = "bbs_id"),
    @Index(name = "IDX_BBS_ARTICLE_PARENT_NTT_ID", columnList = "parent_ntt_id"),
    @Index(name = "IDX_BBS_ARTICLE_NOTICE_STATE", columnList = "notice_state"),
    @Index(name = "IDX_BBS_ARTICLE_PUBLISH_STATE", columnList = "publish_state"),
    @Index(name = "IDX_BBS_ARTICLE_CONTENT", columnList = "content")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbsArticleDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long nttId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bbs_id", nullable = false)
    private BbsMasterDomain bbsMaster;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_ntt_id")
    private BbsArticleDomain parentArticle;

    @Column(nullable = false)
    private int threadDepth;

    @Column(nullable = false, length = 50)
    private String writer;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "notice_state", nullable = false, length = 1)
    private String noticeState;

    @Column
    private LocalDateTime noticeStartDt;

    @Column
    private LocalDateTime noticeEndDt;

    @Column(name = "publish_state", nullable = false, length = 1)
    private String publishState;

    @Column
    private LocalDateTime publishStartDt;

    @Column
    private LocalDateTime publishEndDt;

    @Column(length = 255)
    private String externalLink;

    @Column(nullable = false)
    private int hits;

    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean hasImageInContent = false;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private cms.menu.domain.Menu menu;

    public void update(String writer, String title, String content, String noticeState,
                      LocalDateTime noticeStartDt, LocalDateTime noticeEndDt, String publishState,
                      LocalDateTime publishStartDt, LocalDateTime publishEndDt, String externalLink,
                      boolean hasImage) {
        if (publishStartDt != null && publishEndDt != null && publishEndDt.isBefore(publishStartDt)) {
            throw new IllegalArgumentException("게시 종료일은 게시 시작일보다 이후여야 합니다.");
        }

        if (externalLink != null && !externalLink.isEmpty()) {
            try {
                new URL(externalLink);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("유효하지 않은 URL 형식입니다.");
            }
        }

        this.writer = writer;
        this.title = title;
        this.content = content;
        this.noticeState = noticeState;
        this.noticeStartDt = noticeStartDt;
        this.noticeEndDt = noticeEndDt;
        this.publishState = publishState;
        this.publishStartDt = publishStartDt;
        this.publishEndDt = publishEndDt;
        this.externalLink = externalLink;
        this.hasImageInContent = hasImage;
    }

    public void increaseHits() {
        this.hits++;
    }

    public void setContent(String content) {
        this.content = content;
    }
} 