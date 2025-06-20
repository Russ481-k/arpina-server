package cms.user.domain;

import javax.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import cms.enroll.domain.Enroll;

import javax.persistence.Lob;

@Entity
@Table(name = "user")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User implements UserDetails {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Enroll> enrolls;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private UserRoleType role;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "organization_id", length = 36)
    private String organizationId;

    @Column(name = "group_id", length = 36)
    private String groupId;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "car_no", length = 50)
    private String carNo;

    @Column(name = "temp_pw_flag", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean tempPwFlag;

    @Column(name = "birth_date", length = 8) // YYYYMMDD
    private String birthDate;

    @Column(name = "di", length = 255) // Encrypted DI length might be longer
    private String di;

    @Column(name = "provider", length = 50) // Added provider field
    private String provider;

    @Column(name = "reset_token", length = 255)
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @Column(name = "is_temporary", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isTemporary = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "uuid")
    private User createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "uuid")
    private User updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Lob
    @Column(name = "memo")
    private String memo;

    @Column(name = "memo_updated_at")
    private LocalDateTime memoUpdatedAt;

    @Column(name = "memo_updated_by", length = 36)
    private String memoUpdatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"LOCKED".equals(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }

    public void setIsTemporary(boolean isTemporary) {
        this.isTemporary = isTemporary;
    }

    public void update(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }
}