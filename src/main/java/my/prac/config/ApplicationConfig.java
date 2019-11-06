package my.prac.config;

import javax.sql.DataSource;

import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.DataSourceLookupFailureException;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

//Spring core에 대한 설정
@Configuration
// @PropertySource("classpath:/config/app.properties")
@ComponentScan({ "my.prac.api.*", "my.prac.core.*" })
@MapperScan({ "my.prac" })
@Import({ TransactionConfig.class })
@EnableTransactionManagement
public class ApplicationConfig {
	static Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

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
