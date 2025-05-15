package egov.com.security;

import cms.user.dto.CustomUserDetails;
import cms.user.domain.User;
import cms.user.domain.UserRoleType;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * fileName       : CustomAuthenticationPrincipalResolver
 * author         : crlee
 * date           : 2023/07/13
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2023/07/13        crlee       최초 생성
 */
public class CustomAuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class) &&
                parameter.getParameterType().equals(CustomUserDetails.class);
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null ||
                authentication.getPrincipal() == null ||
                "anonymousUser".equals(authentication.getPrincipal())
        ) {
            User anonymousUser = User.builder()
                .username("anonymous")
                .password("")
                .email("")
                .name("Anonymous User")
                .role(UserRoleType.GUEST)
                .status("ACTIVE")
                .build();
            return new CustomUserDetails(anonymousUser);
        }

        return authentication.getPrincipal();
    }
}
