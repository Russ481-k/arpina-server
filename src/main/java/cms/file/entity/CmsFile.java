package cms.file.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "file")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CmsFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "menu", nullable = false, length = 30)
    private String menu;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "origin_name", nullable = false, length = 255)
    private String originName;

    @Column(name = "saved_name", nullable = false, length = 255, unique = true)
    private String savedName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size", nullable = false)
    private Long size;

    @Column(name = "ext", nullable = false, length = 20)
    private String ext;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "public_yn", length = 1)
    private String publicYn = "Y";

    @Column(name = "file_order")
    private Integer fileOrder = 0;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    @UpdateTimestamp
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
} 