package cms.popup.controller;

import cms.common.dto.ApiResponseSchema;
import cms.popup.dto.PopupRes;
import cms.popup.service.PopupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cms/popups")
@RequiredArgsConstructor
@Tag(name = "User Popup", description = "사용자 팝업 조회 API")
public class PopupController {

    private final PopupService popupService;

    @Operation(summary = "사용자 노출용 활성 팝업 리스트", description = "현재 시간 기준, 노출 가능한 팝업 목록만 반환합니다.")
    @GetMapping("/active")
    public ResponseEntity<ApiResponseSchema<List<PopupRes>>> getActivePopups() {
        List<PopupRes> activePopups = popupService.getActivePopups();
        return ResponseEntity.ok(ApiResponseSchema.success(activePopups, "활성 팝업 목록을 성공적으로 조회했습니다."));
    }
}