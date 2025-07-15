package cms.external.dto;

import cms.user.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserDetailDto {
    private String uuid;
    private String name;
    private String email;
    private String phone;

    public static UserDetailDto from(User user) {
        if (user == null) {
            return null;
        }
        return UserDetailDto.builder()
                .uuid(user.getUuid())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }
}