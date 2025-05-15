package cms.template.validator;

import cms.template.dto.TemplateCellDto;
import cms.template.dto.TemplateRowDto;
import cms.template.exception.InvalidLayoutException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TemplateLayoutValidator {

    public void validateLayout(List<TemplateRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new InvalidLayoutException("Template must have at least one row");
        }

        for (TemplateRowDto row : rows) {
            validateRow(row);
        }
    }

    private void validateRow(TemplateRowDto row) {
        if (row.getCells() == null || row.getCells().isEmpty()) {
            throw new InvalidLayoutException("Row must have at least one cell");
        }

        int totalSpan = 0;
        for (TemplateCellDto cell : row.getCells()) {
            Map<String, Integer> span = cell.getSpan();
            if (span == null || !span.containsKey("base")) {
                throw new InvalidLayoutException("Cell must have a base span value");
            }
            totalSpan += span.get("base");
        }

        if (totalSpan != 12) {
            throw new InvalidLayoutException("Total span of cells in a row must equal 12");
        }
    }
} 