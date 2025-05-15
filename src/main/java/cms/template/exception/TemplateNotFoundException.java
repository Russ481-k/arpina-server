package cms.template.exception;

public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(Long templateId) {
        super(String.format("템플릿을 찾을 수 없습니다. (templateId: %d)", templateId));
    }
} 
 
 
 