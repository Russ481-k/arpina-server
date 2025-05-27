package egov.com.config;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.context.annotation.Primary;

import java.util.Properties;

/**
 * @ClassName : EgovConfigAppJpa.java
 * @Description : JPA 설정
 *
 * @author : 윤주호
 * @since  : 2024. 4. 23
 * @version : 1.0
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일              수정자               수정내용
 *  -------------  ------------   ---------------------
 *   2024. 4. 23    윤주호               최초 생성
 * </pre>
 *
 */
@Configuration
@EnableJpaRepositories(basePackages = {
    "egov.**.repository",
    "cms.**.repository",
    "feature.**.repository"
})
@EnableTransactionManagement
public class EgovConfigAppJpa {

    @Autowired
    private DataSource dataSource;

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan(
            "egov.**.domain",
            "egov.**.entity",
            "cms.**.domain",
            "cms.**.entity", 
            "feature.**.domain",
            "feature.**.entity"
        );
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);
        // Let application.yml control DDL generation
        vendorAdapter.setGenerateDdl(false);
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        // Basic Hibernate Properties
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.jdbc.time_zone", "Asia/Seoul");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        
        // Physical Naming Strategy
        properties.setProperty("hibernate.physical_naming_strategy", 
            "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy");
        
        // Batch Size Configurations
        properties.setProperty("hibernate.jdbc.batch_size", "50");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.batch_versioned_data", "true");
        
        // Query Cache Settings
        properties.setProperty("hibernate.cache.use_second_level_cache", "false");
        properties.setProperty("hibernate.cache.use_query_cache", "false");
        
        // Statement Logging
        properties.setProperty("hibernate.generate_statistics", "true");
        
        em.setJpaProperties(properties);
        
        return em;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
} 