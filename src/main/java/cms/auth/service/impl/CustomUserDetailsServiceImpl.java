package cms.auth.service.impl;

import cms.auth.service.CustomUserDetailsService;
import cms.user.domain.User;
import cms.user.repository.UserRepository;
import cms.user.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Primary;

@Service
@Primary
@RequiredArgsConstructor
public class CustomUserDetailsServiceImpl implements CustomUserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return loadCustomUserByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadCustomUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        return new CustomUserDetails(user);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadCustomUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("이메일로 등록된 사용자를 찾을 수 없습니다."));
        return new CustomUserDetails(user);
    }
} 