package egov.com.config;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

/**
 * @ClassName : EgovConfigAppDataSource.java
 * @Description : DataSource 설정 (통합)
 *
 * @author : 윤주호
 * @since : 2021. 7. 20
 * @version : 1.0
 *
 *          <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일              수정자               수정내용
 *  -------------  ------------   ---------------------
 *   2021. 7. 20    윤주호               최초 생성
 *   2025. 5. 29    통합                 두 설정 파일 통합
 *          </pre>
 *
 */
@Configuration
public class EgovConfigAppDataSource {
    private static final Logger logger = LoggerFactory.getLogger(EgovConfigAppDataSource.class);

    private String dbType;
    private String dbUrl;
    private String dbUsername;
    private String dbPassword;
    private String dbDriverClassName;

    @PostConstruct
    void init() {
        try {
            // Load .env file first
            logger.info("Loading .env file from: {}", System.getProperty("user.dir"));
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")
                    .load();

            // Get database configuration from .env
            this.dbUrl = dotenv.get("SPRING_DATASOURCE_URL");
            this.dbUsername = dotenv.get("SPRING_DATASOURCE_USERNAME");
            this.dbPassword = dotenv.get("SPRING_DATASOURCE_PASSWORD");
            this.dbDriverClassName = "org.mariadb.jdbc.Driver";
            this.dbType = "mariadb";

            // Set all .env variables as system properties for Spring to use
            dotenv.entries().forEach(entry -> {
                System.setProperty(entry.getKey(), entry.getValue());
            });

            // Specifically verify JWT_SECRET is loaded
            String jwtSecret = dotenv.get("JWT_SECRET");
            if (jwtSecret != null) {
                System.setProperty("JWT_SECRET", jwtSecret);
                logger.info("JWT_SECRET successfully loaded from .env");
            } else {
                logger.error("JWT_SECRET not found in .env file");
            }

            logger.info("Database configuration loaded - URL: {}, Username: {}", this.dbUrl, this.dbUsername);
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
    @Bean(name = { "dataSource", "egov.dataSource", "egovDataSource" })
    @Primary
    public DataSource dataSource() {
        logger.info("Creating DataSource with dbType: {}", dbType);
        if ("hsql".equals(dbType)) {
            return dataSourceHSQL();
        } else {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(dbUrl);
            hikariConfig.setUsername(dbUsername);
            hikariConfig.setPassword(dbPassword);
            hikariConfig.setDriverClassName(dbDriverClassName);

            // HikariCP 추가 설정
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);

            logger.info("Creating HikariDataSource with URL: {}", dbUrl);
            return new HikariDataSource(hikariConfig);
        }
    }
}