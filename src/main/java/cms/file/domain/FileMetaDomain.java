package cms.file.domain;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "file")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetaDomain {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FILE_ID")
    private Long fileId;
    
    @Column(name = "MODULE", nullable = false, length = 30)
    private String module;
    
    @Column(name = "MODULE_ID", nullable = false)
    private Long moduleId;
    
    @Column(name = "ORIGIN_NAME", nullable = false, length = 255)
    private String originName;
    
    @Column(name = "SAVED_NAME", nullable = false, length = 255)
    private String savedName;
    
    @Column(name = "MIME_TYPE", nullable = false, length = 100)
    private String mimeType;
    
    @Column(name = "EXT", nullable = false, length = 20)
    private String ext;
    
    @Column(name = "SIZE", nullable = false)
    private Long size;
    
    @Column(name = "VERSION", nullable = false)
    private Integer version;
    
    @Column(name = "PUBLIC_YN", nullable = false)
    private Boolean publicYn;
    
    @Column(name = "THUMB_PATH", length = 255)
    private String thumbPath;
    
    @Column(name = "FILE_ORDER", nullable = false)
    private Integer fileOrder;
    
    @Column(name = "CREATED_BY", length = 36)
    private String createdBy;
    
    @Column(name = "CREATED_IP", length = 45)
    private String createdIp;
    
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "UPDATED_BY", length = 36)
    private String updatedBy;
    
    @Column(name = "UPDATED_IP", length = 45)
    private String updatedIp;
    
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void prePersist() {
        this.version = 1;
        this.publicYn = true;
        this.fileOrder = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
} 