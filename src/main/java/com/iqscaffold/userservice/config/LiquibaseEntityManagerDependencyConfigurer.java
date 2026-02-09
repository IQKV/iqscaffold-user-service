package com.iqscaffold.userservice.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * Configures EntityManagerFactory to depend on SystemLiquibaseInitializer when it exists.
 * 
 * <p>This ensures Liquibase migrations run before JPA initialization without causing
 * failures when SystemLiquibaseInitializer is disabled (e.g., in tests).
 */
@Component
public class LiquibaseEntityManagerDependencyConfigurer implements BeanFactoryPostProcessor {

  private static final String LIQUIBASE_INITIALIZER_BEAN = "systemLiquibaseInitializer";
  private static final String ENTITY_MANAGER_FACTORY_BEAN = "entityManagerFactory";

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    // Only add dependency if SystemLiquibaseInitializer bean exists
    if (beanFactory.containsBean(LIQUIBASE_INITIALIZER_BEAN) 
        && beanFactory.containsBean(ENTITY_MANAGER_FACTORY_BEAN)) {
      
      BeanDefinition emfDefinition = beanFactory.getBeanDefinition(ENTITY_MANAGER_FACTORY_BEAN);
      emfDefinition.setDependsOn(LIQUIBASE_INITIALIZER_BEAN);
    }
  }
}
