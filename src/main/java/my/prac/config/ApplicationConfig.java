package my.prac.config;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.DataSourceLookupFailureException;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.scheduling.annotation.EnableScheduling;

//Spring core에 대한 설정
@Configuration
// @PropertySource("classpath:/config/app.properties")
@ComponentScan(basePackages = { "my.prac.api", "my.prac.core" },
    excludeFilters = @Filter(type = FilterType.ANNOTATION, classes = Controller.class))
@MapperScan({ "my.prac" })
@Import({ TransactionConfig.class })
@EnableTransactionManagement
@EnableScheduling
public class ApplicationConfig {
	static Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

	/** [2026-10-21 성능] BotS5DAO를 요청 범위 읽기 캐시 프록시로 감싼다(같은 요청 안의 동일 쿼리 중복 제거, S5RequestCache 참고). */
	@Bean
	public static BeanPostProcessor s5DaoCachePostProcessor() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) {
				return bean;
			}

			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				if (bean instanceof my.prac.core.prjbot.dao.BotS5DAO) {
					return my.prac.core.prjbot.dao.S5RequestCache.wrap(my.prac.core.prjbot.dao.BotS5DAO.class,
							(my.prac.core.prjbot.dao.BotS5DAO) bean);
				}
				return bean;
			}
		};
	}

	@Bean
	public PlatformTransactionManager transactionManager(DataSource ds) {
		PlatformTransactionManager tm = new DataSourceTransactionManager(ds);
		return tm;
	}

	@Bean
	public DataSource dataSource() throws DataSourceLookupFailureException {
		JndiDataSourceLookup jdsl = new JndiDataSourceLookup();
		jdsl.setResourceRef(true);
		DataSource dataSource = jdsl.getDataSource("jdbc/dev2");
		return dataSource;
	}

	@Bean
	public SqlSessionFactoryBean sqlSessionFactoryBean(DataSource ds) {
		SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
		bean.setDataSource(ds);
		String loc = "/mybatis/mybatis-config.xml";
		bean.setConfigLocation(new ClassPathResource(loc));
		return bean;
	}

	@Bean
	public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactoryBean sfb) throws Exception {
		SqlSessionTemplate template = new SqlSessionTemplate(sfb.getObject());
		return template;
	}

}
