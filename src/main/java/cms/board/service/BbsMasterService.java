package cms.board.service;

import cms.board.dto.BbsMasterDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BbsMasterService {
    BbsMasterDto createBbsMaster(BbsMasterDto bbsMasterDto);
    BbsMasterDto updateBbsMaster(Long bbsId, BbsMasterDto bbsMasterDto);
    void deleteBbsMaster(Long bbsId);
    BbsMasterDto getBbsMaster(Long bbsId);
    Page<BbsMasterDto> getBbsMasters(Pageable pageable);
    Page<BbsMasterDto> searchBbsMasters(String keyword, Pageable pageable);
} 