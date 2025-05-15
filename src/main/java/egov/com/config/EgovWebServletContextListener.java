package egov.com.config;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EgovWebServletContextListener implements ServletContextListener {
	
	public EgovWebServletContextListener() {
		// setEgovProfileSetting(); // 주석 처리
	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		// if (System.getProperty("spring.profiles.active") == null) { // 주석 처리
		// setEgovProfileSetting(); // 주석 처리
		// } // 주석 처리
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		// if (System.getProperty("spring.profiles.active") != null) { // 이 부분은 남겨두거나, 프로파일 설정을 시스템 속성으로 더 이상 하지 않는다면 함께 제거해도 무방합니다. 우선은 남겨둡니다.
		// System.clearProperty("spring.profiles.active");
		// }
	}

	/* // 메소드 전체 주석 처리 시작
	public void setEgovProfileSetting() {
		try {
			log.debug("===========================Start EgovServletContextLoad START ============");
			System.setProperty("spring.profiles.active",
					EgovProperties.getProperty("Globals.DbType") + "," + EgovProperties.getProperty("Globals.Auth"));
			log.debug("Setting spring.profiles.active>" + System.getProperty("spring.profiles.active"));
			log.debug("===========================END   EgovServletContextLoad END ============");
		} catch (IllegalArgumentException e) {
			log.error("[IllegalArgumentException] Try/Catch...usingParameters Runing : " + e.getMessage());
		}
	}
	*/ // 메소드 전체 주석 처리 끝
}
