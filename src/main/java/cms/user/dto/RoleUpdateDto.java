package cms.user.dto;

import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleUpdateDto {
    @Size(min = 2, max = 50, message = "역할 이름은 2자 이상 50자 이하여야 합니다.")
    private String roleName;
    
    @Size(max = 20, message = "역할 타입은 20자 이하여야 합니다.")
    private String roleType;
    private Integer roleLevel;
    @Size(max = 200, message = "설명은 200자 이하여야 합니다.")
    private String description;
    private Boolean isActive;
} 