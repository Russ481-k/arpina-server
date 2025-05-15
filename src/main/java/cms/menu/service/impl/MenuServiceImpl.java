package cms.menu.service.impl;

import cms.menu.domain.Menu;
import cms.menu.domain.MenuType;
import cms.menu.dto.MenuDto;
import cms.menu.dto.MenuOrderDto;
import cms.menu.dto.PageDetailsDto;
import cms.menu.repository.MenuRepository;
import cms.menu.service.MenuService;
import cms.board.domain.BbsMasterDomain;
import cms.board.repository.BbsMasterRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityNotFoundException;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuRepository menuRepository;
    private final BbsMasterRepository bbsMasterRepository;
    private final ModelMapper modelMapper;
    private static final Logger log = LoggerFactory.getLogger(MenuServiceImpl.class);

    @Override
    @Transactional
    public MenuDto createMenu(MenuDto menuDto) {
        // 부모 ID 유효성 검사
        Long parentId = menuDto.getParentId();
        if (parentId != null && (parentId <= 0 || !menuRepository.existsById(parentId))) {
            parentId = null;
        }

        Menu menu = Menu.builder()
                .name(menuDto.getName())
                .type(menuDto.getType())
                .url(menuDto.getUrl())
                .targetId(menuDto.getTargetId())
                .displayPosition(menuDto.getDisplayPosition())
                .visible(menuDto.getVisible())
                .sortOrder(menuDto.getSortOrder())
                .parentId(parentId)
                .build();
        
        Menu savedMenu = menuRepository.save(menu);
        return convertToDto(savedMenu);
    }

    @Override
    @Transactional
    public MenuDto updateMenu(Long id, MenuDto menuDto) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu not found: " + id));
        
        // 이름 중복 체크 제거하고 필드 업데이트
        menu.setName(menuDto.getName());
        menu.setUrl(menuDto.getUrl());
        menu.setType(menuDto.getType());
        menu.setVisible(menuDto.getVisible());
        menu.setSortOrder(menuDto.getSortOrder());
        menu.setDisplayPosition(menuDto.getDisplayPosition());
        menu.setTargetId(menuDto.getTargetId());
        
        // 부모 ID 유효성 검사
        Long parentId = menuDto.getParentId();
        if (parentId != null && (parentId <= 0 || !menuRepository.existsById(parentId))) {
            parentId = null;
        }
        menu.setParentId(parentId);
        
        return convertToDto(menuRepository.save(menu));
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        menuRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public MenuDto getMenu(Long id) {
        return menuRepository.findById(id)
                .map(this::convertToDto)
                .orElseThrow(() -> new RuntimeException("Menu not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuDto> getMenus() {
        List<Menu> menus = menuRepository.findAll();
        return menus.stream()
                .map(menu -> modelMapper.map(menu, MenuDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuDto> getMenuTree() {
        List<Menu> menus = menuRepository.findAll();
        return buildMenuTree(menus);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuDto> getActiveMenus() {
        return menuRepository.findAll()
                .stream()
                .filter(menu -> menu.getVisible() == null || menu.getVisible())
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateMenuActive(Long id, boolean visible) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu not found"));
        
        menu.setVisible(visible);
        menuRepository.save(menu);
    }

    @Override
    @Transactional
    public void updateMenuOrder(Long id, int sortOrder) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu not found"));
        
        menu.setSortOrder(sortOrder);
        menuRepository.save(menu);
    }

    @Override
    @Transactional
    public List<MenuDto> updateMenuOrders(List<MenuOrderDto> orders) {
        List<Menu> updatedMenus = new ArrayList<>();
        
        for (MenuOrderDto order : orders) {
            Menu menu = menuRepository.findById(order.getId())
                    .orElseThrow(() -> new RuntimeException("Menu not found: " + order.getId()));
            
            if (order.getTargetId() != null) {
                Menu targetMenu = menuRepository.findById(order.getTargetId())
                        .orElseThrow(() -> new RuntimeException("Target menu not found: " + order.getTargetId()));
                
                switch (order.getPosition()) {
                    case "before":
                        menu.setSortOrder(targetMenu.getSortOrder() - 1);
                        break;
                    case "after":
                        menu.setSortOrder(targetMenu.getSortOrder() + 1);
                        break;
                    case "inside":
                        menu.setParentId(targetMenu.getId());
                        menu.setSortOrder(0);
                        break;
                    default:
                        throw new RuntimeException("Invalid position: " + order.getPosition());
                }
            } else {
                // 최상위 메뉴로 이동
                menu.setParentId(null);
                menu.setSortOrder(0);
            }
            
            updatedMenus.add(menuRepository.save(menu));
        }
        
        // 순서 재정렬
        List<Menu> allMenus = menuRepository.findAll();
        for (Menu menu : allMenus) {
            if (menu.getParentId() == null) {
                updateSortOrder(menu, allMenus);
            }
        }
        
        return updatedMenus.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    private void updateSortOrder(Menu menu, List<Menu> allMenus) {
        List<Menu> siblings = allMenus.stream()
                .filter(m -> m.getParentId() == menu.getParentId())
                .sorted(Comparator.comparing(Menu::getSortOrder))
                .collect(Collectors.toList());
        
        for (int i = 0; i < siblings.size(); i++) {
            Menu sibling = siblings.get(i);
            sibling.setSortOrder(i);
            menuRepository.save(sibling);
        }
    }

    private List<MenuDto> buildMenuTree(List<Menu> menus) {
        List<MenuDto> rootMenus = menus.stream()
                .filter(menu -> menu.getParentId() == null)
                .map(this::convertToDto)
                .collect(Collectors.toList());

        for (MenuDto rootMenu : rootMenus) {
            buildChildren(rootMenu, menus);
        }

        return rootMenus;
    }

    private void buildChildren(MenuDto parent, List<Menu> allMenus) {
        List<MenuDto> children = allMenus.stream()
                .filter(menu -> parent.getId().equals(menu.getParentId()))
                .map(this::convertToDto)
                .collect(Collectors.toList());

        parent.setChildren(children);
        for (MenuDto child : children) {
            buildChildren(child, allMenus);
        }
    }

    private MenuDto convertToDto(Menu menu) {
        MenuDto dto = new MenuDto();
        dto.setId(menu.getId());
        dto.setName(menu.getName());
        dto.setType(menu.getType());
        dto.setUrl(menu.getUrl());
        dto.setTargetId(menu.getTargetId());
        dto.setDisplayPosition(menu.getDisplayPosition());
        dto.setVisible(menu.getVisible() != null ? menu.getVisible() : true);
        dto.setSortOrder(menu.getSortOrder());
        dto.setParentId(menu.getParentId());
        dto.setChildren(new ArrayList<>());
        
        log.debug("Converting Menu to DTO - ID: {}, Name: {}, DB Visible: {}, DTO Visible: {}, MenuVisible: {}", 
            menu.getId(), menu.getName(), menu.getVisible(), dto.isVisible(), dto.getMenuVisible());
            
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuDto> getActiveMenusByType(String type) {
        return menuRepository.findByTypeAndVisibleTrue(type)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MenuDto> getMenusByType(MenuType type, Pageable pageable) {
        return menuRepository.findByType(type, pageable)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDetailsDto getPageDetailsByMenuId(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new EntityNotFoundException("Menu not found with id: " + menuId));

        PageDetailsDto.PageDetailsDtoBuilder dtoBuilder = PageDetailsDto.builder()
                .menuId(menu.getId())
                .menuName(menu.getName())
                .menuType(menu.getType());

        if (MenuType.BOARD.equals(menu.getType())) {
            if (menu.getTargetId() == null) {
                // Consider using a more specific exception or error handling
                throw new IllegalStateException("Menu with type BOARD has no targetId for menuId: " + menuId);
            }
            BbsMasterDomain board = bbsMasterRepository.findById(menu.getTargetId())
                    .orElseThrow(() -> new EntityNotFoundException("Board not found with id: " + menu.getTargetId() + " for menuId: " + menuId));
            
            // Assuming BbsMasterDomain.getSkinType() returns an Enum.
            // If it returns a String, just use board.getSkinType().
            String skinType = board.getSkinType() != null ? board.getSkinType().name() : null;

            dtoBuilder.boardId(board.getBbsId())
                      .boardName(board.getBbsName())
                      .boardSkinType(skinType)
                      .boardReadAuth(board.getReadAuth())
                      .boardWriteAuth(board.getWriteAuth())
                      .boardAttachmentLimit(board.getAttachmentLimit())
                      .boardAttachmentSize(board.getAttachmentSize());

        } else if (MenuType.CONTENT.equals(menu.getType())) {
            // TODO: Implement logic for CONTENT type
            // e.g., fetch content details from ContentRepository using menu.getTargetId()
            // dtoBuilder.contentId(content.getId()).contentLayout(content.getLayout());
            log.warn("Page details for CONTENT type menu (id: {}) not yet implemented.", menuId);
        } else if (MenuType.PROGRAM.equals(menu.getType())) {
            // TODO: Implement logic for PROGRAM type
            // e.g., fetch program details from ProgramRepository using menu.getTargetId()
            // dtoBuilder.programPath(program.getPath()).programDescription(program.getDescription());
            log.warn("Page details for PROGRAM type menu (id: {}) not yet implemented.", menuId);
        } else {
            // Handle other menu types or unknown types if necessary
            log.warn("Page details for MenuType {} (menuId: {}) not supported.", menu.getType(), menuId);
        }

        return dtoBuilder.build();
    }
} 