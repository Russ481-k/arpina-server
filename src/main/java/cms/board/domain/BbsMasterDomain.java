package cms.board.domain;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bbs_master")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BbsMasterDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bbsId;

    @Column(nullable = false, length = 100)
    private String bbsName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BbsSkinType skinType;

    @Column(nullable = false, length = 50)
    private String readAuth;

    @Column(nullable = false, length = 50)
    private String writeAuth;

    @Column(nullable = false, length = 50)
    private String adminAuth;

    @Column(nullable = false, length = 1)
    private String displayYn;

    @Column(nullable = false, length = 1)
    private String sortOrder;

    @Column(nullable = false, length = 1)
    private String noticeYn;

    @Column(nullable = false, length = 1)
    private String publishYn;

    @Column(nullable = false, length = 1)
    private String attachmentYn;

    @Column(nullable = false)
    private int attachmentLimit;

    @Column(nullable = false)
    private int attachmentSize;

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

    public void update(String bbsName, BbsSkinType skinType, String readAuth, String writeAuth, 
                      String adminAuth, String displayYn, String sortOrder, String noticeYn,
                      String publishYn, String attachmentYn, int attachmentLimit, int attachmentSize) {
        this.bbsName = bbsName;
        this.skinType = skinType;
        this.readAuth = readAuth;
        this.writeAuth = writeAuth;
        this.adminAuth = adminAuth;
        this.displayYn = displayYn;
        this.sortOrder = sortOrder;
        this.noticeYn = noticeYn;
        this.publishYn = publishYn;
        this.attachmentYn = attachmentYn;
        this.attachmentLimit = attachmentLimit;
        this.attachmentSize = attachmentSize;
    }

    public enum BbsSkinType {
        BASIC,  // 기본 게시판
        FAQ,    // 자주 묻는 질문
        QNA,    // 질문과 답변
        PRESS,  // 보도자료
        FORM    // 서식/자료실
    } 
} 