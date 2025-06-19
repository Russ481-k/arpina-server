package cms.mainmedia.service;

import cms.mainmedia.dto.MainMediaRequestDto;
import cms.mainmedia.dto.MainMediaResponseDto;

import java.util.List;

public interface MainMediaService {
    MainMediaResponseDto createMainMedia(MainMediaRequestDto requestDto);

    MainMediaResponseDto getMainMedia(Long id);

    List<MainMediaResponseDto> getAllMainMedia();

    MainMediaResponseDto updateMainMedia(Long id, MainMediaRequestDto requestDto);

    void deleteMainMedia(Long id);
}