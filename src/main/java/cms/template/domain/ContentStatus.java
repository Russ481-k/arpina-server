package cms.template.domain;

public enum ContentStatus {
    DRAFT("초안"),
    PUBLISHED("발행됨"),
    ARCHIVED("보관됨");

    private final String description;

    ContentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
} 