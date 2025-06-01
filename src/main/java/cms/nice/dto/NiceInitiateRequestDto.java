package cms.nice.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NiceInitiateRequestDto {
    private String serviceType; // "REGISTER", "FIND_ID", "RESET_PASSWORD"
} 