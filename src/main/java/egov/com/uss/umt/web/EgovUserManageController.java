package egov.com.uss.umt.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import egov.com.uss.umt.service.EgovUserManageService;
import cms.user.domain.User;
import egov.com.uss.umt.dto.UserSearchDto;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/egov/user")
@RequiredArgsConstructor
@Tag(name = "egov_00_User", description = "사용자 관리 API")        
public class EgovUserManageController {

    private final EgovUserManageService userManageService;

    @GetMapping
    public ResponseEntity<List<User>> listUsers(UserSearchDto searchDto) {
        List<User> users = userManageService.selectUserList(searchDto);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(@PathVariable String userId) {
        User user = userManageService.selectUser(userId);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        userManageService.insertUser(user);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(
            @PathVariable String userId,
            @RequestBody User user) {
        user.setUsername(userId);
        userManageService.updateUser(user);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userManageService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    public ResponseEntity<Integer> getUserCount(UserSearchDto searchDto) {
        int count = userManageService.selectUserListTotCnt(searchDto);
        return ResponseEntity.ok(count);
    }
} 