package cms.board.service.impl;

import cms.board.dto.BbsCategoryDto;
import cms.board.repository.BbsCategoryRepository;
import cms.board.service.BbsCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BbsCategoryServiceImpl implements BbsCategoryService {

    private final BbsCategoryRepository bbsCategoryRepository;

    @Override
    public List<BbsCategoryDto> getCategoriesByBbsId(Long bbsId) {
        return bbsCategoryRepository.findAllByBbsMaster_BbsIdAndDisplayYnOrderBySortOrderAsc(bbsId, "Y").stream()
                .map(category -> BbsCategoryDto.builder()
                        .categoryId(category.getCategoryId())
                        .code(category.getCode())
                        .name(category.getName())
                        .build())
                .collect(Collectors.toList());
    }
}