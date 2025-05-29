package egov.com.config;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

/**
 * @ClassName : EgovConfigAppDataSource.java
 * @Description : DataSource 설정 (통합)
 *
 * @author : 윤주호
 * @since  : 2021. 7. 20
 * @version : 1.0
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일              수정자               수정내용
 *  -------------  ------------   ---------------------
 *   2021. 7. 20    윤주호               최초 생성
 *   2025. 5. 29    통합                 두 설정 파일 통합
 * </pre>
 *
 */
@Configuration
public class EgovConfigAppDataSourcee {
    private static final Logger logger = LoggerFactory.getLogger(EgovConfigAppDataSourcee.class);

    @Autowired
    Environment env;

    private String dbType;

    @PostConstruct
    void init() {
        try {
            // Load .env file first
            logger.info("Loading .env file from: {}", System.getProperty("user.dir"));
            Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .load();
            
            // Set system properties from .env
            String dbUrl = dotenv.get("SPRING_DATASOURCE_URL");
            String dbUsername = dotenv.get("SPRING_DATASOURCE_USERNAME");
            String dbPassword = dotenv.get("SPRING_DATASOURCE_PASSWORD");
            
            logger.info("Setting system properties from .env");
            System.setProperty("SPRING_DATASOURCE_URL", dbUrl);
            System.setProperty("SPRING_DATASOURCE_USERNAME", dbUsername);
            System.setProperty("SPRING_DATASOURCE_PASSWORD", dbPassword);
            System.setProperty("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.mariadb.jdbc.Driver");
            
            this.dbType = "mariadb";
            
            logger.info("Database configuration loaded - URL: {}, Username: {}", dbUrl, dbUsername);
        } catch (Exception e) {
            logger.error("Error loading environment variables", e);
            throw new RuntimeException("Failed to load environment variables", e);
        }
    }

    /**
     * @return [dataSource 설정] HSQL 설정
     */
    private DataSource dataSourceHSQL() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.HSQL)
            .setScriptEncoding("UTF8")
            .addScript("classpath:/db/shtdb.sql")
            .build();
    }

    /**
     * Primary DataSource using HikariCP
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource() {
        logger.info("Creating DataSource with dbType: {}", dbType);
        if ("hsql".equals(dbType)) {
            return dataSourceHSQL();
        } else {
            return DataSourceBuilder.create()
                    .type(HikariDataSource.class)
                    .build();
        }
    }
} 