package cms.board.service.impl;

import cms.board.domain.BbsMasterDomain;
import cms.board.dto.BbsMasterDto;
import cms.board.repository.BbsMasterRepository;
import cms.board.service.BbsMasterService;
import cms.common.exception.BbsMasterNotFoundException;
import cms.menu.repository.MenuRepository;
import cms.menu.domain.Menu;
import cms.menu.domain.MenuType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BbsMasterServiceImpl implements BbsMasterService {

    private final BbsMasterRepository bbsMasterRepository;
    private final MenuRepository menuRepository;

    @Override
    @Transactional
    public BbsMasterDto createBbsMaster(BbsMasterDto bbsMasterDto) {
        BbsMasterDomain bbsMaster = BbsMasterDomain.builder()
                .bbsName(bbsMasterDto.getBbsName())
                .skinType(bbsMasterDto.getSkinType())
                .readAuth(bbsMasterDto.getReadAuth())
                .writeAuth(bbsMasterDto.getWriteAuth())
                .adminAuth(bbsMasterDto.getAdminAuth())
                .displayYn(bbsMasterDto.getDisplayYn())
                .sortOrder(bbsMasterDto.getSortOrder())
                .noticeYn("N")
                .publishYn("N")
                .attachmentYn("N")
                .attachmentLimit(bbsMasterDto.getAttachmentLimit())
                .attachmentSize(bbsMasterDto.getAttachmentSize())
                .build();

        BbsMasterDomain savedBbsMaster = bbsMasterRepository.save(bbsMaster);
        return convertToDto(savedBbsMaster);
    }

    @Override
    @Transactional
    public BbsMasterDto updateBbsMaster(Long bbsId, BbsMasterDto bbsMasterDto) {
        // 1. 게시판 정보 확인 및 업데이트
        BbsMasterDomain bbsMaster = bbsMasterRepository.findById(bbsId)
                .orElseThrow(() -> new BbsMasterNotFoundException(bbsId));

        bbsMaster.update(
                bbsMasterDto.getBbsName(),
                bbsMasterDto.getSkinType(),
                bbsMasterDto.getReadAuth(),
                bbsMasterDto.getWriteAuth(),
                bbsMasterDto.getAdminAuth(),
                bbsMasterDto.getDisplayYn(),
                bbsMasterDto.getSortOrder(),
                bbsMasterDto.getNoticeYn(),
                bbsMasterDto.getPublishYn(),
                bbsMasterDto.getAttachmentYn(),
                bbsMasterDto.getAttachmentLimit(),
                bbsMasterDto.getAttachmentSize()
        );

        // 2. 메뉴 정보 업데이트 (menuId가 있는 경우에만)
        if (bbsMasterDto.getMenuId() != null) {
            Menu menu = menuRepository.findById(bbsMasterDto.getMenuId())
                    .orElseThrow(() -> new RuntimeException("Menu not found with id: " + bbsMasterDto.getMenuId()));
            menu.updateTargetId(bbsId);
            menuRepository.save(menu);
        }

        return convertToDto(bbsMaster);
    }

    @Override
    @Transactional
    public void deleteBbsMaster(Long bbsId) {
        BbsMasterDomain bbsMaster = bbsMasterRepository.findById(bbsId)
                .orElseThrow(() -> new BbsMasterNotFoundException(bbsId));
        bbsMasterRepository.delete(bbsMaster);
    }

    @Override
    @Transactional(readOnly = true)
    public BbsMasterDto getBbsMaster(Long bbsId) {
        BbsMasterDomain bbsMaster = bbsMasterRepository.findById(bbsId)
                .orElseThrow(() -> new BbsMasterNotFoundException(bbsId));
        return convertToDto(bbsMaster);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BbsMasterDto> getBbsMasters(Pageable pageable) {
        return bbsMasterRepository.findAll(pageable)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BbsMasterDto> searchBbsMasters(String keyword, Pageable pageable) {
        return bbsMasterRepository.findByBbsNameContaining(keyword, pageable)
                .map(this::convertToDto);
    }

    private BbsMasterDto convertToDto(BbsMasterDomain bbsMaster) {
        // Find the associated menu using the corrected repository method
        Optional<Menu> menuOptional = menuRepository.findFirstByTypeAndTargetId(MenuType.BOARD, bbsMaster.getBbsId());
        Long menuId = menuOptional.map(Menu::getId).orElse(null); // Extract menuId or null if not found

        return BbsMasterDto.builder()
                .bbsId(bbsMaster.getBbsId())
                .menuId(menuId) // Set the found menuId
                .bbsName(bbsMaster.getBbsName())
                .skinType(bbsMaster.getSkinType())
                .readAuth(bbsMaster.getReadAuth())
                .writeAuth(bbsMaster.getWriteAuth())
                .adminAuth(bbsMaster.getAdminAuth())
                .displayYn(bbsMaster.getDisplayYn())
                .sortOrder(bbsMaster.getSortOrder())
                .noticeYn(bbsMaster.getNoticeYn())
                .publishYn(bbsMaster.getPublishYn())
                .attachmentYn(bbsMaster.getAttachmentYn())
                .attachmentLimit(bbsMaster.getAttachmentLimit())
                .attachmentSize(bbsMaster.getAttachmentSize())
                // Assuming extraSchema is not needed for public view, or handle if needed
                .build();
    }
} 