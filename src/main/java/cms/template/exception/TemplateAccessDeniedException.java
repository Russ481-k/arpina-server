package cms.template.exception;

public class TemplateAccessDeniedException extends RuntimeException {
    public TemplateAccessDeniedException(Long templateId) {
        super(String.format("템플릿에 대한 접근 권한이 없습니다. (templateId: %d)", templateId));
    }
} 