package cms.board.service;

import cms.board.dto.BbsCategoryDto;

import java.util.List;

public interface BbsCategoryService {
    List<BbsCategoryDto> getCategoriesByBbsId(Long bbsId);
}