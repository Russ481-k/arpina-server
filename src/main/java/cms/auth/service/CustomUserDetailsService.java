package cms.auth.service;

import org.springframework.security.core.userdetails.UserDetailsService;
import cms.user.dto.CustomUserDetails;

public interface CustomUserDetailsService extends UserDetailsService {
    CustomUserDetails loadCustomUserByUsername(String username);
    CustomUserDetails loadCustomUserByEmail(String email);
} 