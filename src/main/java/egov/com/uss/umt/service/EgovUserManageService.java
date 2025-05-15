package egov.com.uss.umt.service;

import java.util.List;

import cms.user.domain.User;
import egov.com.uss.umt.dto.UserSearchDto;

public interface EgovUserManageService {
    /**
     * 사용자 정보를 조회한다.
     * @param userId 사용자ID
     * @return User 사용자 정보
     */
    User selectUser(String userId);

    /**
     * 사용자 정보를 등록한다.
     * @param user 사용자 정보
     */
    void insertUser(User user);

    /**
     * 사용자 정보를 수정한다.
     * @param user 사용자 정보
     */
    void updateUser(User user);

    /**
     * 사용자 정보를 삭제한다.
     * @param userId 사용자ID
     */
    void deleteUser(String userId);

    /**
     * 사용자 목록을 조회한다.
     * @param searchDto 검색조건
     * @return List<User> 사용자 목록
     */
    List<User> selectUserList(UserSearchDto searchDto);

    /**
     * 사용자 총 갯수를 조회한다.
     * @param searchDto 검색조건
     * @return int 사용자 총 갯수
     */
    int selectUserListTotCnt(UserSearchDto searchDto);
} 