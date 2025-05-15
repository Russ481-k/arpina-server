package cms.template.domain;

import lombok.*;
import javax.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "TEMPLATE_ROW")
public class TemplateRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rowId;

    @Column(nullable = false)
    private int ordinal;

    private Integer heightPx;
    private String bgColor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @OneToMany(mappedBy = "row", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TemplateCell> cells = new ArrayList<>();

    public void addCell(TemplateCell cell) {
        cells.add(cell);
        cell.setRow(this);
    }
} 