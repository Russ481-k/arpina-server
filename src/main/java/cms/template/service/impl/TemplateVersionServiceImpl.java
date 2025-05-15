package cms.template.service.impl;

import cms.template.domain.TemplateCell;
import cms.template.domain.TemplateRow;
import cms.template.domain.Template;
import cms.template.repository.TemplateRepository;
import cms.template.service.TemplateVersionService;
import cms.template.exception.TemplateVersionNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateVersionServiceImpl implements TemplateVersionService {

    private final TemplateRepository templateRepository;

    @Override
    @Transactional
    public Template createNewVersion(Template existingTemplate) {
        Template newVersion = Template.builder()
            .templateName(existingTemplate.getTemplateName())
            .description(existingTemplate.getDescription())
            .type(existingTemplate.getType())
            .published(existingTemplate.isPublished())
            .versionNo(existingTemplate.getVersionNo() + 1)
            .build();

        existingTemplate.getRows().forEach(row -> {
            TemplateRow newRow = TemplateRow.builder()
                .ordinal(row.getOrdinal())
                .heightPx(row.getHeightPx())
                .bgColor(row.getBgColor())
                .build();
            newVersion.addRow(newRow);

            row.getCells().forEach(cell -> {
                TemplateCell newCell = TemplateCell.builder()
                    .ordinal(cell.getOrdinal())
                    .span(cell.getSpan())
                    .widgetId(cell.getWidgetId())
                    .build();
                newRow.addCell(newCell);
            });
        });

        return templateRepository.save(newVersion);
    }

    @Override
    @Transactional
    public Template rollbackToVersion(Template template, int versionNo) {
        // Find the target version
        List<Template> oldVersions = templateRepository.findOldVersionsByTemplateName(
            template.getTemplateName(),
            template.getVersionNo()
        );

        Template targetVersion = oldVersions.stream()
            .filter(v -> v.getVersionNo() == versionNo)
            .findFirst()
            .orElseThrow(() -> new TemplateVersionNotFoundException(template.getTemplateId(), versionNo));

        // Create a new version from the target version
        Template newVersion = Template.builder()
            .templateName(targetVersion.getTemplateName())
            .description(targetVersion.getDescription())
            .type(targetVersion.getType())
            .published(targetVersion.isPublished())
            .versionNo(template.getVersionNo() + 1)
            .build();

        // Copy rows and cells
        targetVersion.getRows().forEach(row -> {
            TemplateRow newRow = TemplateRow.builder()
                .ordinal(row.getOrdinal())
                .heightPx(row.getHeightPx())
                .bgColor(row.getBgColor())
                .build();
            newVersion.addRow(newRow);

            row.getCells().forEach(cell -> {
                TemplateCell newCell = TemplateCell.builder()
                    .ordinal(cell.getOrdinal())
                    .span(cell.getSpan())
                    .widgetId(cell.getWidgetId())
                    .build();
                newRow.addCell(newCell);
            });
        });

        return templateRepository.save(newVersion);
    }
} 