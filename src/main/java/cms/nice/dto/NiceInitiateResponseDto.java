package cms.nice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class NiceInitiateResponseDto {
    private String encodeData;
    private String reqSeq;
} 