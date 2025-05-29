package cms.auth.service.impl;

import cms.auth.service.CustomUserDetailsService;
import cms.user.domain.User;
import cms.user.repository.UserRepository;
import cms.user.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsServiceImpl.class);

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("Loading UserDetails for username (via loadUserByUsername): {}", username);
        CustomUserDetails customUserDetails = loadCustomUserByUsername(username);
        if (customUserDetails != null && customUserDetails.getUser() != null) {
            logger.debug("User found: {}, Gender from DB (via CustomUserDetails.getUser().getGender()): {}", customUserDetails.getUsername(), customUserDetails.getUser().getGender());
        } else {
            logger.warn("CustomUserDetails or its internal User object is null after loading for username: {}", username);
        }
        return customUserDetails;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadCustomUserByUsername(String username) {
        logger.debug("Loading CustomUserDetails for username: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.warn("User not found with username: {}", username);
                    return new UsernameNotFoundException("사용자를 찾을 수 없습니다. Username: " + username);
                });
        logger.info("User object loaded from DB for username: {}. Gender directly from user.getGender(): {}", username, user.getGender());
        
        if (user.getGender() == null || user.getGender().trim().isEmpty()) {
            logger.warn("Gender is null or empty for user: {} AFTER loading from DB. This is likely the root cause.", username);
        }
        
        return new CustomUserDetails(user);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadCustomUserByEmail(String email) {
        logger.debug("Loading CustomUserDetails for email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("User not found with email: {}", email);
                    return new UsernameNotFoundException("이메일로 등록된 사용자를 찾을 수 없습니다. Email: " + email);
                });
        logger.info("User object loaded from DB for email: {}. Gender directly from user.getGender(): {}", user.getUsername(), user.getGender());

        if (user.getGender() == null || user.getGender().trim().isEmpty()) {
            logger.warn("Gender is null or empty for user (by email): {} AFTER loading from DB. This is likely the root cause.", user.getUsername());
        }

        return new CustomUserDetails(user);
    }
} 