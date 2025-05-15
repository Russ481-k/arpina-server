package cms.common.exception;

public class MainPageNotFoundException extends RuntimeException {
    public MainPageNotFoundException(Long id) {
        super("메인 페이지를 찾을 수 없습니다. (id: " + id + ")");
    }

    public MainPageNotFoundException(String message) {
        super(message);
    }
} 