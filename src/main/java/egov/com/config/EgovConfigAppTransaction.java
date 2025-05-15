package egov.com.config;

import javax.persistence.EntityManagerFactory;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.Properties;

/**
 * @Class Name : EgovConfigAppTransaction.java
 * @Description : 트랜잭션 설정
 * @Modification Information
 * @
 * @  수정일              수정자              수정내용
 * @ ----------   ---------   -------------------------------
 * @ 2024.04.25   김수빈              최초생성
 *
 * @author 김수빈
 * @since 2024.04.25
 * @version 1.0
 * @see
 *
 */
@Configuration
@EnableAspectJAutoProxy
public class EgovConfigAppTransaction {

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Bean
	public PlatformTransactionManager transactionManager() {
		return new JpaTransactionManager(entityManagerFactory);
	}

	@Bean
	public TransactionInterceptor txAdvice(PlatformTransactionManager transactionManager) {
		TransactionInterceptor txAdvice = new TransactionInterceptor();
		txAdvice.setTransactionManager(transactionManager);
		
		Properties txAttributes = new Properties();
		txAttributes.setProperty("*", "PROPAGATION_REQUIRED");
		txAttributes.setProperty("get*", "PROPAGATION_REQUIRED,readOnly");
		txAttributes.setProperty("select*", "PROPAGATION_REQUIRED,readOnly");
		txAttributes.setProperty("insert*", "PROPAGATION_REQUIRED,-Exception");
		txAttributes.setProperty("update*", "PROPAGATION_REQUIRED,-Exception");
		txAttributes.setProperty("delete*", "PROPAGATION_REQUIRED,-Exception");
		
		txAdvice.setTransactionAttributes(txAttributes);
		return txAdvice;
	}

	@Bean
	public Advisor txAdvisor(TransactionInterceptor txAdvice) {
		AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
		pointcut.setExpression("execution(* egov.com..service.*Impl.*(..))");
		return new DefaultPointcutAdvisor(pointcut, txAdvice);
	}
}
