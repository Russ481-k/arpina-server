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
    private String memo;
    private LocalDateTime memoUpdatedAt;
    private String memoUpdatedBy;
}