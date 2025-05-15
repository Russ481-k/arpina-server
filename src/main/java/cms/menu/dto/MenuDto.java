package cms.menu.dto;

import cms.menu.domain.MenuType;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class MenuDto {
    private Long id;
    private Long parentId;
    
    @NotBlank(message = "메뉴명은 필수 입력값입니다.")
    @Size(max = 100, message = "메뉴명은 100자 이내여야 합니다.")
    private String name;
    
    @Size(max = 200, message = "메뉴 URL은 200자 이내여야 합니다.")
    private String url;
    
    private Integer sortOrder;
    private Boolean visible;
    private MenuType type;
    
    // 트리 구조를 위한 필드
    private List<MenuDto> children;
    
    // 메뉴 연결 정보
    private Long targetId;
    private String displayPosition;
    
    // 메타 정보
    private String createdBy;
    private String updatedBy;

    private Long menuId;
    private String menuNm;
    private Integer menuOrdr;
    private String useAt;
    private String menuDesc;
    private String menuIcon;
    private String target;
    private String menuLevel;
    private String menuPath;
    private String menuQuery;
    private String menuParam;
    private String menuStyle;
    private String menuClass;
    private String menuScript;
    private String menuPermission;
    private String menuVisible;
    private String menuSort;
    private String menuCache;
    private String menuAuth;
    private String menuLog;
    private String menuStatus;
    private String menuCreated;
    private String menuModified;
    private String menuDeleted;
    private String menuVersion;
    private String menuRemark;

    // 추가된 getter/setter 메서드들
    public String getMenuNm() {
        return menuNm;
    }

    public void setMenuNm(String menuNm) {
        this.menuNm = menuNm;
    }

    public Integer getMenuOrdr() {
        return menuOrdr;
    }

    public void setMenuOrdr(Integer menuOrdr) {
        this.menuOrdr = menuOrdr;
    }

    public String getUseAt() {
        return useAt;
    }

    public void setUseAt(String useAt) {
        this.useAt = useAt;
    }

    public Boolean isVisible() {
        return visible != null ? visible : true;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
        this.menuVisible = visible != null ? (visible ? "Y" : "N") : "Y";
    }
} 