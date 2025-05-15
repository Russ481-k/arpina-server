package cms.template.exception;

public class TemplateVersionNotFoundException extends RuntimeException {
    public TemplateVersionNotFoundException(Long templateId, int versionNo) {
        super(String.format("템플릿 버전을 찾을 수 없습니다. (templateId: %d, versionNo: %d)", templateId, versionNo));
    }
} 