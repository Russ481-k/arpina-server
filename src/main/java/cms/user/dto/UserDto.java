package cms.user.dto;

import cms.user.domain.UserRoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String uuid;
    
    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters")
    private String username;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$", 
            message = "Password must contain at least one letter, one number, and one special character")
    private String password;
    
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;
    
    @NotNull(message = "Role is required")
    @Size(max = 20, message = "Role must not exceed 20 characters")
    private UserRoleType role;
    
    @Size(max = 255, message = "Avatar URL must not exceed 255 characters")
    private String avatarUrl;
    
    @NotBlank(message = "Status is required")
    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status;
    
    @Size(max = 36, message = "Group ID must not exceed 36 characters")
    private String groupId;
    
    private String resetToken;
    private LocalDateTime resetTokenExpiry;
    
    private String createdBy;
    private String createdIp;
    private LocalDateTime createdAt;
    
    private String updatedBy;
    private String updatedIp;
    private LocalDateTime updatedAt;
} 