package egov.com.uss.umt.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import egov.com.cmm.util.EgovFileScrty;

import egov.com.uss.umt.service.EgovUserManageService;
import cms.user.domain.User;
import egov.com.uss.umt.dto.UserSearchDto;
import egov.com.uss.umt.repository.UserManageRepository;

import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;

@Service
public class EgovUserManageServiceImpl implements EgovUserManageService {

    private final UserManageRepository userManageRepository;
    private final EgovIdGnrService idgenService;

    public EgovUserManageServiceImpl(UserManageRepository userManageRepository, @Qualifier("egovIdGnrService") EgovIdGnrService idgenService) {
        this.userManageRepository = userManageRepository;
        this.idgenService = idgenService;
    }

    @Override
    @Transactional(readOnly = true)
    public User selectUser(String userId) {
        return userManageRepository.findByUsername(userId);
    }

    @Override
    @Transactional
    public void insertUser(User user) {
        try {
            // 비밀번호 암호화
            String pass = EgovFileScrty.encryptPassword(user.getPassword(), user.getUsername());
            user.setPassword(pass);

            // ID 생성
            String userId = idgenService.getNextStringId();
            user.setUuid(userId);

            userManageRepository.save(user);
        } catch (Exception e) {
            throw new RuntimeException("사용자 등록 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    @Transactional
    public void updateUser(User user) {
        try {
            // 비밀번호가 변경된 경우에만 암호화
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                String pass = EgovFileScrty.encryptPassword(user.getPassword(), user.getUsername());
                user.setPassword(pass);
            }
            
            userManageRepository.save(user);
        } catch (Exception e) {
            throw new RuntimeException("사용자 정보 수정 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    @Transactional
    public void deleteUser(String userId) {
        try {
            User user = userManageRepository.findByUsername(userId);
            if (user != null) {
                userManageRepository.delete(user);
            }
        } catch (Exception e) {
            throw new RuntimeException("사용자 삭제 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> selectUserList(UserSearchDto searchDto) {
        Pageable pageable = PageRequest.of(
            searchDto.getPageIndex() - 1,
            searchDto.getPageSize()
        );
        
        Page<User> page;
        if (searchDto.getSearchCondition() != null && searchDto.getSearchKeyword() != null) {
            page = userManageRepository.searchUsers(
                searchDto.getSearchCondition(),
                searchDto.getSearchKeyword(),
                pageable
            );
        } else {
            page = userManageRepository.findAll(pageable);
        }
        
        return page.getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public int selectUserListTotCnt(UserSearchDto searchDto) {
        if (searchDto.getSearchCondition() != null && searchDto.getSearchKeyword() != null) {
            Page<User> page = userManageRepository.searchUsers(
                searchDto.getSearchCondition(),
                searchDto.getSearchKeyword(),
                PageRequest.of(0, Integer.MAX_VALUE)
            );
            return (int) page.getTotalElements();
        }
        return (int) userManageRepository.count();
    }
} 