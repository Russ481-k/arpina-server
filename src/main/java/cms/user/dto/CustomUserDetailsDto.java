package cms.user.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class CustomUserDetailsDto implements UserDetails {
    private static final long serialVersionUID = -8274004534207618049L;
    
    private String id;
    private String name;
    private String email;
    private String password;
    private String userSe;
    private String groupId;
    private String groupNm;
    private String ip;
    private String dn;
    private LocalDateTime lastLoginDate;
    private LocalDateTime passwordChangeDate;
    private int loginFailCount;
    private boolean isLocked;
    private boolean isEnabled;
    private List<String> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        return id;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !isLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }
} 