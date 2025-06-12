package cms.popup.controller;

import cms.popup.dto.*;
import cms.popup.service.PopupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cms/popups")
@RequiredArgsConstructor
@Tag(name = "Admin Popup", description = "관리자 팝업 관리 API")
public class AdminPopupController {

    private final PopupService popupService;

    @Operation(summary = "관리자용 팝업 전체 목록 조회")
    @GetMapping
    public ResponseEntity<List<AdminPopupRes>> getPopups() {
        return ResponseEntity.ok(popupService.getPopupsForAdmin());
    }

    @Operation(summary = "팝업 상세 조회")
    @GetMapping("/{popupId}")
    public ResponseEntity<PopupDto> getPopup(@PathVariable Long popupId) {
        return ResponseEntity.ok(popupService.getPopup(popupId));
    }

    @Operation(summary = "팝업 생성", description = "multipart/form-data 형식으로 팝업 정보와 미디어 파일을 등록합니다.")
    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<Map<String, Object>> createPopup(
            @RequestPart("popupData") PopupDataReq popupData,
            @RequestPart("content") String contentJson,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "mediaLocalIds", required = false) String[] mediaLocalIds) {

        PopupDto createdPopup = popupService.createPopup(popupData, contentJson, mediaFiles, mediaLocalIds);

        Map<String, Object> response = new HashMap<>();
        response.put("id", createdPopup.getId());
        response.put("message", "팝업이 등록되었습니다.");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(summary = "팝업 수정")
    @PutMapping(value = "/{popupId}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<Void> updatePopup(
            @PathVariable Long popupId,
            @RequestPart("popupData") PopupUpdateReq popupUpdateReq,
            @RequestPart("content") String contentJson,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "mediaLocalIds", required = false) String[] mediaLocalIds) {

        popupService.updatePopup(popupId, popupUpdateReq, contentJson, mediaFiles, mediaLocalIds);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "팝업 삭제")
    @DeleteMapping("/{popupId}")
    public ResponseEntity<Map<String, String>> deletePopup(@PathVariable Long popupId) {
        popupService.deletePopup(popupId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "팝업이 삭제되었습니다.");

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "팝업 노출 여부 변경")
    @PatchMapping("/{popupId}/visibility")
    public ResponseEntity<Void> updateVisibility(@PathVariable Long popupId, @RequestBody PopupVisibilityReq req) {
        popupService.updateVisibility(popupId, req);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "팝업 순서 일괄 변경")
    @PatchMapping("/order")
    public ResponseEntity<Void> updateOrder(@RequestBody PopupOrderReq req) {
        popupService.updateOrder(req);
        return ResponseEntity.ok().build();
    }
}