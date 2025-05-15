package cms.board.domain;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "bbs_article_category")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbsArticleCategoryDomain {

    @EmbeddedId
    private BbsArticleCategoryId id;

    @MapsId("nttId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ntt_id", nullable = false)
    private BbsArticleDomain article;

    @MapsId("categoryId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private BbsCategoryDomain category;

    @Embeddable
    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class BbsArticleCategoryId implements java.io.Serializable {
        private Long nttId;
        private Long categoryId;
    }
} 