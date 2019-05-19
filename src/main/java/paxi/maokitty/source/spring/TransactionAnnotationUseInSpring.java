package paxi.maokitty.source.spring;

import paxi.maokitty.source.annotation.*;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/5/12.
 */
@Background(
        target = "了解spring中事务注解transactional源码的实现方式",
        conclusion = "spring在扫描tx标签的时候，碰到transactional标注的类或者方法，会创建对应的AOP代理，在调用的时候则是AOP代理去执行，先按照AOP的方式执行相应的逻辑，再执行用户定义的方法，如果有问题则执行对应的事务",
        sourceCodeProjectName = "spring-framework",
        sourceCodeAddress = "https://github.com/spring-projects/spring-framework",
        projectVersion = "5.1.1.RELEASE"
)
public class TransactionAnnotationUseInSpring {
    @Main
    @Trace(
            index = 0,
            originClassName = "org.springframework.beans.factory.xml.BeanDefinitionParserDelegate",
            function = "public BeanDefinition parseCustomElement(Element ele, @Nullable BeanDefinition containingBd)",
            introduction = "spring的xml文件加载的时候，就会读取到对应的xml节点，根据节点名字的不同，使用不同的方式来解析",
            more = "sping源码启动分析 https://juejin.im/post/5bd446d7f265da0a8b576a24"
    )
    public void parseCustomElement(){
        //...
        Code.SLICE.source("NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri)")
                .interpretation("获取读取到的xml节点名字对应的解析器,使用对应的解析器来解析找到的bean,对于事务来讲，至关重要的就是 <tx:annotation-driven transaction-manager=\"txManagerTest\"/> 节点,它对应的处理类就是 TxNamespaceHandler。" +
                        "从TxNamespaceHandler的实现来看，对不同的属性，比如 annotation-driven ,则自己去实现的bean为 AnnotationDrivenBeanDefinitionParser ,以作为后续备用 ");
        //...
        Code.SLICE.source("return handler.parse(ele, new ParserContext(this.readerContext, this, containingBd))")
                .interpretation("开始解析这个标签，对应的内部实行解析的也就是 AnnotationDrivenBeanDefinitionParser ");
    }
    
    @Trace(
            index = 1,
            originClassName = "org.springframework.transaction.config.AnnotationDrivenBeanDefinitionParser",
            function = "public BeanDefinition parse(Element element, ParserContext parserContext) "
    )
    public void parse(){
        Code.SLICE.source("registerTransactionalEventListenerFactory(parserContext);")
                .interpretation("首先干的一件事情就是注册事务的监听器工厂，TransactionalEventListenerFactory，创建");
        //...
        Code.SLICE.source("if (\"aspectj\".equals(mode)) {\n" +
                "   //aspectj处理" +
                "  }\n" +
                "  else {\n" +
                "   AopAutoProxyConfigurer.configureAutoProxyCreator(element, parserContext);\n" +
                "  }")
                .interpretation("获取xml的mode属性，可以看到对 aspectj 的支持，如果没有则默认使用 proxy 的模式");
    }

    @Trace(
            index = 2,
            originClassName = "org.springframework.transaction.config.AopAutoProxyConfigurer",
            function = "public static void configureAutoProxyCreator(Element element, ParserContext parserContext) "
    )
    @KeyPoint(desc = "事务就是用到了spring自己的AOP")
    public void configureAutoProxyCreator(){
        //...
        Code.SLICE.source("AopNamespaceUtils.registerAutoProxyCreatorIfNecessary(parserContext, element);")
                .interpretation("在beanFactory中添加 InfrastructureAdvisorAutoProxyCreator，作为后面创建自动代理用");
        //...
        Code.SLICE.source("RootBeanDefinition sourceDef = new RootBeanDefinition(org.springframework.transaction.annotation.AnnotationTransactionAttributeSource\");")
                .interpretation("获取以及解析Transactional标签");
        //...
        Code.SLICE.source("registerTransactionManager(element, interceptorDef);")
                .interpretation("获取tx 标签中的transaction-manager，它的配置可以使用 spring 的org.springframework.jdbc.datasource.DataSourceTransactionManager");
        //...
        Code.SLICE.source("RootBeanDefinition interceptorDef = new RootBeanDefinition(TransactionInterceptor.class);")
                .interpretation("创建事务拦截器,它实现了aop的MethodInterceptor，也就是说如果拦截到了它将在方法前后做相应的处理");
        //...
        Code.SLICE.source("RootBeanDefinition advisorDef = new RootBeanDefinition(BeanFactoryTransactionAttributeSourceAdvisor.class)")
                .interpretation("创建的就是一个 spring aop 的一个通知,它的通知拦截器 则是 TransactionInterceptor ");
        //...
    }

    @Trace(
            index = 3,
            originClassName = "org.springframework.transaction.annotation.AnnotationTransactionAttributeSource",
            function = "public AnnotationTransactionAttributeSource(boolean publicMethodsOnly) "
    )
    public void  AnnotationTransactionAttributeSource(){
        // ...
        Code.SLICE.source("if (jta12Present || ejb3Present) {\n" +
                "   this.annotationParsers = new LinkedHashSet<>(4);\n" +
                "   this.annotationParsers.add(new SpringTransactionAnnotationParser());\n" +
                "   if (jta12Present) {\n" +
                "    this.annotationParsers.add(new JtaTransactionAnnotationParser());\n" +
                "   }\n" +
                "   if (ejb3Present) {\n" +
                "    this.annotationParsers.add(new Ejb3TransactionAnnotationParser());\n" +
                "   }\n" +
                "  }\n" +
                "  else {\n" +
                "   this.annotationParsers = Collections.singleton(new SpringTransactionAnnotationParser());\n" +
                "  } ")
                .interpretation("看看是否存在 javax.transaction.Transactional 和 javax.ejb.TransactionAttribute 这两个标签，如果有的话就添加对用的 注解解析器，另外不管怎样都会添加spring自己的注解解析器 ");
    }

    @Trace(
            index = 4,
            originClassName = "org.springframework.transaction.annotation.SpringTransactionAnnotationParser",
            function = "protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) "
    )
    public void parseTransactionAnnotation(){
        //...
        Code.SLICE.source("Propagation propagation = attributes.getEnum(\"propagation\");")
                .interpretation("拿到有spring Transactional标注的方法和类后，读取对应的 传播参数、隔离级别等等作为后续备用");
    }

    @Trace(
            index = 5,
            originClassName = "org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory",
            function = "protected Object createBean(String beanName, RootBeanDefinition mbd, Object[] args) throws BeanCreationException ",
            introduction = "加载完xml之后，开始spring自己的单例懒加载，@link(paxi.maokitty.source.spring.SingletonOfSpringBean),在执行类的初始化,单例最终会执行到 createBean "
    )
    @Recall(
            traceIndex = 2,
            tip = "事务利用的原理是AOP，在bean的初始化声明周期中，AOP在后置处理器有对应的操作"
    )
    public void createBean(){
        //...
        Code.SLICE.source("Object beanInstance = doCreateBean(beanName, mbdToUse, args);")
                .interpretation("createBean本质上只是一个壳子，最终的实现还是在方法 doCreateBean ,再延续到里面的 initializeBean ，这里就涉及到了 bean 的生命周期，这里关注 postProcessAfterInitialization");
        //...
    }


    @Trace(
            index = 6,
            originClassName = "org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory",
            function = "public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)throws BeansException"
    )
    @Recall(traceIndex = 2,tip = "读到 tx 注解的时候，就插入了对 AOP 的后置处理")
    public void applyBeanPostProcessorsAfterInitialization(){
        //...
        Code.SLICE.source("for (BeanPostProcessor beanProcessor : getBeanPostProcessors()) {\n" +
                                    "result = beanProcessor.postProcessAfterInitialization(result, beanName);")
                .interpretation("就是获取所有的后置处理器，每一个遍历执行。注意到之前AOP添加的InfrastructureAdvisorAutoProxyCreator，它的父类 AbstractAdvisorAutoProxyCreator 的父类 AbstractAutoProxyCreator 就有对AOP的后置处理");
        //...
    }

    @Trace(
            index = 7,
            originClassName = "org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator",
            function = "public Object postProcessAfterInitialization(@Nullable Object bean, String beanName)"
    )
    public void  postProcessAfterInitialization(){
        //...
        Code.SLICE.source("return wrapIfNecessary(bean, beanName, cacheKey);")
                .interpretation("给原始的bean做些处理");
        //...
    }
    
    @Trace(
            index = 8,
            originClassName = "org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator",
            function = "protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) ",
            introduction = "ProxyFactory是AOP的代理工厂，方便用户通过写代码的方式来实现AOP逻辑"
    )
    public void  wrapIfNecessary(){
        //...
        Code.SLICE.source("Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);")
                .interpretation("获取目标类的 advisor");
        //...
        Code.SLICE.source("this.advisedBeans.put(cacheKey, Boolean.TRUE);\n" +
                "   Object proxy = createProxy(\n" +
                "     bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));\n" +
                "   this.proxyTypes.put(cacheKey, proxy.getClass());")
                .interpretation("如果目标类存在 advidor,首先做个标记，同时创建这个advisor的代理工厂:ProxyFactory，再通过它来获取目标类的代理，spring的AOP如果是类的实现，返回的则是使用 CglibAopProxy创建的代理" +
                        ",另外，对aop,则是使用DynamicAdvisedInterceptor拦截器来处理对应的AOP逻辑,它自身也会保存这个 ProxyFactory 到字段 advised");
        //...
    }

    @Trace(
            index = 10,
            originClassName = " org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator",
            function = "protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) ",
            introduction = "负责找对应类是够存在的Advisor"
    )
    @RecallArr(recalls = {
            @Recall( traceIndex = 2,tip = "系统自动找到加载的时候创建的BeanFactoryTransactionAttributeSourceAdvisor"),
            @Recall( traceIndex = 4,  tip = "spring自带的解析或查到含有 transactional注解的方法或者类然后读取信息" )
        }
    )
    @KeyPoint(
            desc = "在创建bean代理的时候，如果是接口则使用JDK动态代理，否则使用CGLIB，返回被Transactional修饰的类的代理，只是增加了对应的回调方法，至此事务创建完毕"
    )
    public void findEligibleAdvisors(){
        //...
        Code.SLICE.source("List<Advisor> candidateAdvisors = findCandidateAdvisors();")
                .interpretation("找到BeanFactory中注册过的advisor，比如之前提到过的事务相关的 BeanFactoryTransactionAttributeSourceAdvisor");
        Code.SLICE.source("List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);")
                .interpretation("找到bean中有被transactional注解修饰过的方法或者是类,然后返回");
        //...
    }

    @Trace(
            index = 11,
            originClassName = " org.springframework.aop.framework.DynamicAdvisedInterceptor",
            function = "public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable ",
            introduction = "以CGLIBE为例，此处为spring中的CglibAopProxy执行的Interceptor",
            more = "更多动态代理详见: https://juejin.im/post/5c4d3f28f265da6142743f9e "
    )
    @RecallArr(recalls={
            @Recall( traceIndex = 2,tip = "系统自动找到加载的时候创建的就是TransactionInterceptor"),
            @Recall( traceIndex = 8,tip = "CGLIB在AOP处理时会回调DynamicAdvisedInterceptor")
    })
    public void  intercept(){
        //...
        Code.SLICE.source("List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);")
                .interpretation("得到拦截器去执行，这里就是利用bean的通知BeanFactoryTransactionAttributeSourceAdvisor，再拿到对应的环绕拦截器TransactionInterceptor，来执行事务相关的操作");
        //...
        Code.SLICE.source("retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();")
                .interpretation("执行被代理的方法,最后拿到返回值");
        //...
    }

    @Trace(
            index = 12,
            originClassName = " org.springframework.transaction.interceptor.TransactionInterceptor",
            function = "public Object invoke(final MethodInvocation invocation) throws Throwable"
    )

    public void txInvoke(){
            //...
        Code.SLICE.source("return invokeWithinTransaction(invocation.getMethod(), targetClass, new InvocationCallback() {\n" +
                "      @Override\n" +
                "      public Object proceedWithInvocation() throws Throwable {\n" +
                "        return invocation.proceed();\n" +
                "      }\n" +
                "    });").interpretation("先执行事务的代理，然后执行被代理的方法，这里的invocation就是被代理的用户自己写的方法");
    }

    @Trace(
            index = 13,
            originClassName = "org.springframework.transaction.interceptor.TransactionAspectSupport",
            function = "protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,final InvocationCallback invocation) throws Throwable"
    )
    public void  invokeWithinTransaction(){
        //...
        Code.SLICE.source("final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);")
                .interpretation("查到对应方法的事务配置");
        Code.SLICE.source("final PlatformTransactionManager tm = determineTransactionManager(txAttr);")
                .interpretation("拿到transactionManager,比如用户在xml中配置的 org.springframework.jdbc.datasource.DataSourceTransactionManager");
        Code.SLICE.source("final String joinpointIdentification = methodIdentification(method, targetClass);")
                .interpretation("获取transaction标注的方法");
        //...
        Code.SLICE.source("TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);\n" +
                "   Object retVal = null;\n" +
                "   try {\n" +
                "    retVal = invocation.proceedWithInvocation();\n" +
                "   }\n" +
                "   catch (Throwable ex) {\n" +
                "    // target invocation exception\n" +
                "    completeTransactionAfterThrowing(txInfo, ex);\n" +
                "    throw ex;\n" +
                "   }\n" +
                "   finally {\n" +
                "    cleanupTransactionInfo(txInfo);\n" +
                "   }\n" +
                "   commitTransactionAfterReturning(txInfo);\n" +
                "   return retVal;")
                .interpretation("这里就是标准的事务处理流程  1：获取事务；2：执行用户自己的方法；3：如果执行过程中抛出了异常执行异常抛出后的事务处理逻辑 4：清除事务信息 5：提交事务");
        //...
    }

}
