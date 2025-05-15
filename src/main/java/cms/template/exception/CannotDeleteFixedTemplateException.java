package cms.template.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CannotDeleteFixedTemplateException extends RuntimeException {
    public CannotDeleteFixedTemplateException() {
        super("고정 템플릿은 삭제할 수 없습니다.");
    }
} 