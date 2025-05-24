package cms.admin.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMemoDto {
    private String userUuid;
    private String memoContent;
    private LocalDateTime updatedAt;
    private String updatedByAdminId; // 관리자 식별자 (ID 또는 이름 등)
} 