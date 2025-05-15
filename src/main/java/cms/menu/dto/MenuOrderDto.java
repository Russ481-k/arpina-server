package cms.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuOrderDto {
    @NotNull(message = "메뉴 ID는 필수입니다.")
    private Long id;
    
    private Long targetId;  // null 허용 (최상위 레벨로 이동)
    
    @NotNull(message = "위치는 필수입니다.")
    @Pattern(regexp = "^(before|after|inside)$", message = "위치는 before, after, inside 중 하나여야 합니다.")
    private String position;
} 