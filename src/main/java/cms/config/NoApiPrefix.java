package cms.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 애노테이션이 붙은 컨트롤러 클래스에는 전역 API 프리픽스(/api/v1)를 적용하지 않습니다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoApiPrefix {
}


