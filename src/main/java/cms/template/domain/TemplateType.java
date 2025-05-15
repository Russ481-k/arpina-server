package cms.template.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "템플릿 타입")
public enum TemplateType {
    @Schema(description = "메인 템플릿")
    MAIN,    // 메인 템플릿
    @Schema(description = "서브 템플릿")
    SUB,     // 서브 템플릿
    @Schema(description = "커스텀 템플릿")
    CUSTOM   // 커스텀 템플릿
} 