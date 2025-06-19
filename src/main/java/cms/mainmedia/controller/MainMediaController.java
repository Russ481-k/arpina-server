package cms.mainmedia.controller;

import cms.common.dto.ApiResponseSchema;
import cms.mainmedia.dto.MainMediaRequestDto;
import cms.mainmedia.dto.MainMediaResponseDto;
import cms.mainmedia.service.MainMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "CMS - Main Media Management", description = "메인 미디어 관리 API")
@RestController
@RequestMapping("/cms/main-media")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN', 'SYSTEM_ADMIN')")
public class MainMediaController {

    private final MainMediaService mainMediaService;

    @PostMapping
    @Operation(summary = "메인 미디어 생성")
    public ResponseEntity<ApiResponseSchema<MainMediaResponseDto>> createMainMedia(
            @Valid @RequestBody MainMediaRequestDto requestDto) {
        MainMediaResponseDto createdMedia = mainMediaService.createMainMedia(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseSchema.success(createdMedia, "메인 미디어가 성공적으로 생성되었습니다."));
    }

    @GetMapping("/{id}")
    @Operation(summary = "메인 미디어 상세 조회")
    public ResponseEntity<ApiResponseSchema<MainMediaResponseDto>> getMainMedia(@PathVariable Long id) {
        MainMediaResponseDto mainMedia = mainMediaService.getMainMedia(id);
        return ResponseEntity.ok(ApiResponseSchema.success(mainMedia));
    }

    @GetMapping
    @Operation(summary = "메인 미디어 전체 조회")
    public ResponseEntity<ApiResponseSchema<List<MainMediaResponseDto>>> getAllMainMedia() {
        List<MainMediaResponseDto> allMainMedia = mainMediaService.getAllMainMedia();
        return ResponseEntity.ok(ApiResponseSchema.success(allMainMedia));
    }

    @PutMapping("/{id}")
    @Operation(summary = "메인 미디어 수정")
    public ResponseEntity<ApiResponseSchema<MainMediaResponseDto>> updateMainMedia(@PathVariable Long id,
            @Valid @RequestBody MainMediaRequestDto requestDto) {
        MainMediaResponseDto updatedMedia = mainMediaService.updateMainMedia(id, requestDto);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedMedia, "메인 미디어가 성공적으로 수정되었습니다."));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "메인 미디어 삭제")
    public ResponseEntity<ApiResponseSchema<Void>> deleteMainMedia(@PathVariable Long id) {
        mainMediaService.deleteMainMedia(id);
        return ResponseEntity.ok(ApiResponseSchema.success(null, "메인 미디어가 성공적으로 삭제되었습니다."));
    }
}