package com.exam.springdeepdive.beancycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class LifecycleBean implements BeanNameAware, BeanFactoryAware, ApplicationContextAware, InitializingBean, DisposableBean {

    public LifecycleBean() {
        System.out.println("1. Constructor [LifecycleBean]");
    }

    @Override
    public void setBeanName(String name) {
        System.out.println("2. setBeanName: " + name + " [LifecycleBean]");
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        System.out.println("3. setBeanFactory [LifecycleBean]");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        System.out.println("4. setApplicationContext [LifecycleBean]");
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("6. @PostConstruct [LifecycleBean]");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("7. afterPropertiesSet (InitializingBean) [LifecycleBean]");
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("9. @PreDestroy [LifecycleBean]");
    }

    @Override
    public void destroy() throws Exception {
        System.out.println("10. destroy (DisposableBean) [LifecycleBean]");
    }
}
