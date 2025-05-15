알겠습니다! **템플릿 관리 API**에 필요한 **JPA Entity** 추가를 반영한 **최종 문서**를 작성하겠습니다. 이제 데이터베이스와 연동되는 **템플릿 관리** API의 백엔드 환경을 고려하여 **JPA Entity** 관련 내용을 포함한 최종 문서를 작성하겠습니다.

---

## 📑 **Template (템플릿) API v1 — Final Spec** (2025-04-29)

> **Base URL** `/api/v1` | **Auth** `Authorization: Bearer {JWT}`  
> 모든 엔드포인트는 HTTPS + JWT 필수.  
> 전역 **`ADMIN`** 이상, 템플릿별 **세부 권한**(`readAuth / writeAuth / adminAuth`)을 추가로 검증합니다.

---

### 1. **템플릿 UX & 역할**

| `role`     | 대표 용도       | 설명                                | 특이사항                  |
| ---------- | --------------- | ----------------------------------- | ------------------------- |
| `NORMAL`   | 일반 템플릿     | 일반적인 템플릿                    | –                         |
| `MAIN`     | 메인 템플릿     | 사이트의 메인 페이지 템플릿        | 삭제 불가, 수정만 가능   |
| `SUB`      | 서브 템플릿     | 사이트의 서브 페이지 템플릿        | 삭제 불가, 수정만 가능   |

---

### 2. **Endpoints**

### 3.1 **템플릿 마스터 관리**

| Method | URL                            | Req.Body         | Resp         | 권한    |
| ------ | ------------------------------ | ---------------- | ------------ | ------- |
| GET    | `/cms/templates/master`        | –                | List         | `ADMIN` |
| POST   | `/cms/templates/master`        | `TemplateDto`    | Created      | `ADMIN` |
| GET    | `/cms/templates/master/{id}`   | –                | `TemplateDto` | 관리    |
| PUT    | `/cms/templates/master/{id}`   | `TemplateDto`    | Updated      | 관리    |
| DELETE | `/cms/templates/master/{id}`   | –                | 204          | 관리    |

---

### 3.2 **템플릿 관리**

| Method | URL                              | Req.Body         | Resp         | 설명         |
| ------ | --------------------------------- | ---------------- | ------------ | ------------ |
| GET    | `/cms/templates/{templateId}`    | –                | `TemplateDto` | 템플릿 세부  |
| POST   | `/cms/templates`                 | `TemplateDto`    | Created      | 템플릿 작성  |
| PUT    | `/cms/templates/{templateId}`    | `TemplateDto`    | Updated      | 템플릿 수정  |
| DELETE | `/cms/templates/{templateId}`    | –                | 204          | 템플릿 삭제  |

---

### 3.3 **템플릿 레이아웃 관리**

| Method | URL                                     | Req.Body         | Resp         | 권한    |
| ------ | --------------------------------------- | ---------------- | ------------ | ------- |
| PUT    | `/cms/templates/{templateId}/layout`    | `LayoutDto`      | 200          | `ADMIN` |

---

### 3.4 **템플릿 삭제 및 복제**

| Method | URL                              | Req.Body | Resp  | 권한    |
| ------ | --------------------------------- | -------- | ----- | ------- |
| DELETE | `/cms/templates/{templateId}`    | –        | 204   | `ADMIN` |
| POST   | `/cms/templates/{templateId}/clone` | –        | Cloned| `ADMIN` |

---

### 3.5 **템플릿 버전 관리**

| Method | URL                                        | Req.Body | Resp    | 권한     |
| ------ | ------------------------------------------ | -------- | ------- | -------- |
| POST   | `/cms/templates/{templateId}/rollback`    | –        | Updated | `ADMIN`  |

---

### 4. **템플릿 DTO**

#### 4.1 **TemplateDto** (템플릿 마스터 데이터 구조)

```json
{
  "templateName": "메인 페이지",
  "role": "NORMAL",  // MAIN, SUB, NORMAL
  "published": true,
  "version": 1,
  "layout": [
    { "rowId": 1, "height": 300, "bgColor": "#FFFFFF", "cells": [ { "cellId": 1, "span": 12 } ] }
  ],
  "createdBy": "admin",
  "createdAt": "2025-05-01T00:00:00+09:00"
}
```

#### 4.2 **LayoutDto** (템플릿 레이아웃 데이터 구조)

```json
{
  "rows": [
    {
      "rowId": 22,
      "heightPx": 500,
      "cells": [
        { "cellId": 103, "span": { "md": 10, "lg": 8 } }
      ]
    }
  ]
}
```

---

### 5. **Response Wrapper**

```json
// List
{ "status": 200, "data": [...], "pagination": { "page":1,"size":20,"total":148 } }
// 단건
{ "status": 200, "data": { ... } }
// Error
{ "status": 404, "error": { "code":"TEMPLATE_NOT_FOUND","message":"템플릿을 찾을 수 없습니다." } }
```

**Error Codes**

| code                 | http | message                  |
| -------------------- | ---- | ------------------------ |
| TEMPLATE_NOT_FOUND    | 404  | 템플릿을 찾을 수 없습니다. |
| CANNOT_DELETE_FIXED_TEMPLATE | 409  | 메인/서브 템플릿은 삭제할 수 없습니다. |
| INVALID_LAYOUT_OVERLAP | 400  | 블록 좌표가 겹칩니다. |
| INVALID_SPAN_OVERFLOW | 400  | 블록 너비 합계가 12를 초과합니다. |

---

### 6. **Database DDL**

```sql
-- 템플릿 정보
CREATE TABLE TEMPLATE (
  TEMPLATE_ID   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 템플릿 ID',
  TEMPLATE_NM   VARCHAR(100) NOT NULL COMMENT '템플릿 이름',
  ROLE          ENUM('MAIN','SUB','NORMAL') DEFAULT 'NORMAL' COMMENT '템플릿 역할 (MAIN, SUB, NORMAL)',
  IS_PUBLISHED  TINYINT(1) DEFAULT 0 COMMENT '템플릿 공개 여부 (0=비공개, 1=공개)',
  VERSION_NO    INT DEFAULT 1 COMMENT '템플릿 버전',
  CREATED_BY    VARCHAR(36)  NULL COMMENT '생성자 ID',
  CREATED_IP    VARCHAR(45)  NULL COMMENT '생성자 IP',
  CREATED_AT    TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  UPDATED_BY    VARCHAR(36)  NULL COMMENT '수정자 ID',
  UPDATED_IP    VARCHAR(45)  NULL COMMENT '수정자 IP',
  UPDATED_AT    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='템플릿 정보';

-- 템플릿 행
CREATE TABLE TEMPLATE_ROW (
  ROW_ID       BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 템플릿 행 ID',
  TEMPLATE_ID  BIGINT NOT NULL COMMENT 'FK: 템플릿 ID (TEMPLATE 테이블 참조)',
  ORDINAL      INT NOT NULL COMMENT '행 순서',
  HEIGHT_PX    INT NULL COMMENT '행 높이 (픽셀 단위)',
  BG_COLOR     VARCHAR(20) NULL COMMENT '행 배경 색상',
  FOREIGN KEY (TEMPLATE_ID) REFERENCES TEMPLATE(TEMPLATE_ID) ON DELETE CASCADE COMMENT 'TEMPLATE 테이블과 연관된 외래 키',
  INDEX IDX_TEMPLATE_ROW (TEMPLATE_ID, ORDINAL) COMMENT '템플릿 ID 및 행 순서에 대한 인덱스'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='템플릿 행 정보';

-- 템플릿 셀
CREATE TABLE TEMPLATE_CELL (
  CELL_ID     BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 셀 ID',
  ROW_ID      BIGINT NOT NULL COMMENT 'FK: 행 ID (TEMPLATE_ROW 테이블 참조)',
  ORDINAL     INT NOT NULL COMMENT '셀 순서',
  SPAN_JSON   JSON NOT NULL CHECK (JSON_VALID(SPAN_JSON)) COMMENT '셀의 너비 및 반응형 브레이크포인트 정보 (base, md, lg 등)',
  WIDGET_ID   BIGINT NULL COMMENT 'FK: 위젯 ID (WIDGET 테이블 참조)',
  FOREIGN KEY (ROW_ID) REFERENCES TEMPLATE_ROW(ROW_ID) ON DELETE CASCADE COMMENT 'TEMPLATE_ROW 테이블과 연관된 외래 키',
  FOREIGN KEY (WIDGET_ID) REFERENCES WIDGET(WIDGET_ID) COMMENT 'WIDGET 테이블과 연관된 외래 키',
  UNIQUE KEY UK_CELL_ORDINAL (ROW_ID, ORDINAL) COMMENT '행 내 셀 순서에 대한 유니크 제약'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='템플릿 셀 정보';
```

---

### **JPA Entity 클래스 요약**

**템플릿** 관리 API는 데이터베이스와의 연동을 위해 **JPA Entity** 클래스를 사용합니다. 이를 통해 템플릿 정보, 템플릿 행, 템플릿 셀 및 위젯 정보를 **데이터베이스 테이블과 매핑**하고, **CRUD 작업**을 수행할 수 있습니다.

#### **JPA Entity 예시**

**Template Entity (템플릿)**

```java
@Entity
@Table(name = "TEMPLATE")
public class Template {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long templateId;

  @Column(nullable = false)
  private String templateName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TemplateRole role; // MAIN, SUB, NORMAL

  @Column(nullable = false)
  private boolean isPublished;

  @Column(nullable = false)
  private int versionNo;

  @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TemplateRow> rows = new ArrayList<>();

  @Column(nullable = false)
  private String createdBy;

  @Column(nullable = false)
  private String createdIp;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private String updatedBy;

  @Column(nullable = false)
  private String updatedIp;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  // Getter, Setter, Constructor 등 생략
}
```

**TemplateRow Entity (템플릿 행)**

```java
@Entity
@Table(name = "TEMPLATE_ROW")
public class TemplateRow {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long rowId;

  @ManyToOne
  @JoinColumn(name = "template_id", nullable = false)
  private Template template;

  @Column(nullable = false)
  private int ordinal;

  @Column(nullable = true)
  private Integer heightPx;

  @Column(nullable = true)
  private String bgColor;

  @OneToMany(mappedBy = "row", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TemplateCell> cells = new ArrayList<>();

  // Getter, Setter, Constructor 등 생략
}
```

**TemplateCell Entity (템플릿 셀)**

```java
@Entity
@Table(name = "TEMPLATE_CELL")
public class TemplateCell {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long cellId;

  @ManyToOne
  @JoinColumn(name = "row_id", nullable = false)
  private TemplateRow row;

  @Column(nullable = false)
  private int ordinal;

  @Convert(converter = SpanJsonConverter.class)
  @Column(nullable = false)
  private Map<String, Integer> span; // 반응형 너비 (base/md/lg)

  @ManyToOne
  @JoinColumn(name = "widget_id")
  private Widget widget;

  // Getter, Setter, Constructor 등 생략
}
```

---

### **요약**

- **JPA Entity**는 **템플릿 관리 API**와 데이터베이스 간의 연동을 가능하게 합니다. 템플릿, 템플릿 행, 템플릿 셀, 위젯의 데이터를 처리하고 CRUD 작업을 수행합니다.
- **Response Wrapper**와 함께 **템플릿 데이터**를 JSON 형식으로 주고받으며, 템플릿의 **버전 관리**, **삭제**, **복제** 등의 기능을 제공합니다.
- 데이터베이스와의 연동을 위해 **DDL** 및 **JPA Entity** 클래스가 필요하며, 이를 통해 효율적으로 템플릿 데이터를 관리할 수 있습니다.
