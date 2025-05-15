package egov;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ServletComponentScan
@ComponentScan(basePackages = {"egov", "cms"})
@SpringBootApplication(scanBasePackages = {"egov.com", "cms", "feature"})
@EnableScheduling
public class EgovBootApplication {

	@Value("${custom-debug.dev-profile-loaded:#{false}}")
	private boolean devProfileLoadedByValue;

	private final Environment environment;

	public EgovBootApplication(Environment environment) {
		this.environment = environment;
	}

	public static void main(String[] args) {
		log.debug("##### EgovBootApplication Start #####");

		SpringApplication springApplication = new SpringApplication(EgovBootApplication.class);
		springApplication.setBannerMode(Banner.Mode.OFF);
		//springApplication.setLogStartupInfo(false);
		springApplication.run(args);

		log.debug("##### EgovBootApplication End #####");
	}

	@Bean
	public CommandLineRunner commandLineRunner() {
		return args -> {
			log.info("##### Custom Debug (via @Value): dev-profile-loaded = {} #####", devProfileLoadedByValue);
			log.info("##### Active Profiles (via Environment): {} #####", String.join(", ", environment.getActiveProfiles()));
			log.info("##### spring.session.store-type (via Environment): {} #####", environment.getProperty("spring.session.store-type"));
		};
	}

}
