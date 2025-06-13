package cms.popup.controller;

import cms.common.dto.ApiResponseSchema;
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
    public ResponseEntity<ApiResponseSchema<List<AdminPopupRes>>> getPopups() {
        List<AdminPopupRes> popups = popupService.getPopupsForAdmin();
        return ResponseEntity.ok(ApiResponseSchema.success(popups, "팝업 목록을 성공적으로 조회했습니다."));
    }

    @Operation(summary = "팝업 상세 조회")
    @GetMapping("/{popupId}")
    public ResponseEntity<ApiResponseSchema<PopupDto>> getPopup(@PathVariable Long popupId) {
        PopupDto popup = popupService.getPopup(popupId);
        return ResponseEntity.ok(ApiResponseSchema.success(popup, "팝업을 성공적으로 조회했습니다."));
    }

    @Operation(summary = "팝업 생성", description = "multipart/form-data 형식으로 팝업 정보와 미디어 파일을 등록합니다.")
    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ApiResponseSchema<Map<String, Object>>> createPopup(
            @RequestPart("popupData") @Valid PopupDataReq popupData,
            @RequestPart("content") String contentJson,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "mediaLocalIds", required = false) String mediaLocalIds) {

        PopupDto createdPopup = popupService.createPopup(popupData, contentJson, mediaFiles, mediaLocalIds);
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("id", createdPopup.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseSchema.success(responseData, "팝업이 등록되었습니다."));
    }

    @Operation(summary = "팝업 수정")
    @PutMapping(value = "/{popupId}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ApiResponseSchema<Void>> updatePopup(
            @PathVariable Long popupId,
            @RequestPart("popupData") @Valid PopupUpdateReq popupUpdateReq,
            @RequestPart(value = "content", required = false) String contentJson,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "mediaLocalIds", required = false) String mediaLocalIds) {

        popupService.updatePopup(popupId, popupUpdateReq, contentJson, mediaFiles, mediaLocalIds);
        return ResponseEntity.ok(ApiResponseSchema.success("팝업이 성공적으로 수정되었습니다."));
    }

    @Operation(summary = "팝업 삭제")
    @DeleteMapping("/{popupId}")
    public ResponseEntity<ApiResponseSchema<Void>> deletePopup(@PathVariable Long popupId) {
        popupService.deletePopup(popupId);
        return ResponseEntity.ok(ApiResponseSchema.success("팝업이 삭제되었습니다."));
    }

    @Operation(summary = "팝업 노출 여부 변경")
    @PatchMapping("/{popupId}/visibility")
    public ResponseEntity<ApiResponseSchema<Void>> updateVisibility(@PathVariable Long popupId,
            @RequestBody @Valid PopupVisibilityReq req) {
        popupService.updateVisibility(popupId, req);
        return ResponseEntity.ok(ApiResponseSchema.success("팝업 노출 여부가 성공적으로 변경되었습니다."));
    }

    @Operation(summary = "팝업 순서 일괄 변경")
    @PatchMapping("/order")
    public ResponseEntity<ApiResponseSchema<Void>> updateOrder(@RequestBody @Valid PopupOrderReq req) {
        popupService.updateOrder(req);
        return ResponseEntity.ok(ApiResponseSchema.success("팝업 순서가 성공적으로 변경되었습니다."));
    }
}