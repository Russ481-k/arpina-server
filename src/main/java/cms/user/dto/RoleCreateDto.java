package cms.user.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleCreateDto {
    @NotBlank(message = "역할 이름은 필수입니다.")
    @Size(min = 2, max = 50, message = "역할 이름은 2자 이상 50자 이하여야 합니다.")
    private String roleName;
    
    @NotBlank(message = "역할 타입은 필수입니다.")
    @Size(max = 20, message = "역할 타입은 20자 이하여야 합니다.")
    private String roleType;
    
    @NotNull(message = "역할 레벨은 필수입니다.")
    private Integer roleLevel;
    
    @Size(max = 200, message = "설명은 200자 이하여야 합니다.")
    private String description;
    
    private Boolean isActive = true;
} 