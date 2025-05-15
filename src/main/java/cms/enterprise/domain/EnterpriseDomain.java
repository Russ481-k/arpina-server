package cms.enterprise.domain;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "enterprise")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class EnterpriseDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enterprise_id")
    private Long id;

    @Column(name = "year", nullable = false)
    private Integer year; // year는 숫자형

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_path") // image는 경로 또는 URL이므로 image_path 또는 image_url
    private String image;

    @Column(name = "representative")
    private String representative;

    @Column(name = "established_date") // established는 날짜
    private LocalDate established;

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "detail_content", columnDefinition = "MEDIUMTEXT") // detail은 내용이 길 수 있음
    private String detail;

    @Column(name = "show_button", nullable = false)
    @Builder.Default
    private Boolean showButton = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50) // 사용자 ID 길이에 맞게 조절
    private String createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "updated_by", length = 50) // 사용자 ID 길이에 맞게 조절
    private String updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    // createdBy, createdIp, updatedBy, updatedIp 등 감사(Audit) 정보 필드 추가 고려
    // 예: ScheduleDomain 처럼
    // @Column(name = "created_by", length = 36)
    // private String createdBy;
    //
    // @Column(name = "created_ip", length = 45)
    // private String createdIp;
    //
    // @Column(name = "updated_by", length = 36)
    // private String updatedBy;
    //
    // @Column(name = "updated_ip", length = 45)
    // private String updatedIp;

    // Setter methods for updatable fields
    public void setYear(Integer year) {
        this.year = year;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setRepresentative(String representative) {
        this.representative = representative;
    }

    public void setEstablished(LocalDate established) {
        this.established = established;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public void setShowButton(Boolean showButton) {
        this.showButton = showButton;
    }

    // Setters for audit fields
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setCreatedIp(String createdIp) {
        this.createdIp = createdIp;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public void setUpdatedIp(String updatedIp) {
        this.updatedIp = updatedIp;
    }

    // Update 메소드 (필요에 따라 추가)
    public void updateDetails(Integer year, String name, String description, String image,
                              String representative, LocalDate established, String businessType,
                              String detail, Boolean showButton) {
        this.year = year;
        this.name = name;
        this.description = description;
        this.image = image;
        this.representative = representative;
        this.established = established;
        this.businessType = businessType;
        this.detail = detail;
        this.showButton = showButton;
        // this.updatedBy = updatedBy; // 감사 필드 사용 시
        // this.updatedIp = updatedIp; // 감사 필드 사용 시
    }
} 