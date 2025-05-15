package cms.template.service;

import cms.template.domain.Template;

public interface TemplateVersionService {
    Template createNewVersion(Template existingTemplate);
    Template rollbackToVersion(Template template, int versionNo);
} 