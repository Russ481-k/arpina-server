package cms.template.domain;

public enum WidgetType {
    IMAGE("이미지"),
    TEXT("텍스트"),
    BBS("게시판"),
    VIDEO("비디오"),
    CAROUSEL("캐러셀"),
    BUTTON("버튼");

    private final String description;

    WidgetType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
} 