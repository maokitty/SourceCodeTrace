package paxi.maokitty.source.spring;

import paxi.maokitty.source.annotation.*;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/5/3.
 */
@Background(
        target = "了解spring中，配置bean的使用域为 singleton 时，源码的处理方式",
        conclusion = "",
        sourceCodeProjectName = "spring-framework",
        sourceCodeAddress = "https://github.com/spring-projects/spring-framework",
        projectVersion = "5.1.1.RELEASE"
)
public class SingletonOfSpringBean {

    @Main
    @Trace(
            index = 0,
            originClassName = "org.springframework.context.support.AbstractApplicationContext",
            function = "public void refresh() throws BeansException, IllegalStateException",
            introduction = "spring整体启动过程核心在这里",
            more = "sping源码启动分析 https://juejin.im/post/5bd446d7f265da0a8b576a24"
    )
    public void refresh(){
        //...
        Code.slice("这里负责做两件事情，1是加载定义的bean，2是返回初始化的beanFactory","ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory()");
        //...
        Code.slice("完成bean中尚未完成的类的初始化", "finishBeanFactoryInitialization(beanFactory)");
        //...
    }

    @Trace(
            index = 1,
            originClassName = "org.springframework.context.support.AbstractApplicationContext",
            function = "protected ConfigurableListableBeanFactory obtainFreshBeanFactory()"
    )
    public void obtainFreshBeanFactory(){
        //调用加载的逻辑
        refreshBeanFactory();
        //...
    }

    @Trace(
            index = 2,
            originClassName = "org.springframework.context.support.AbstractRefreshableApplicationContext",
            function = "protected final void refreshBeanFactory() throws BeansException"
    )
    public void refreshBeanFactory(){
        //...
        Code.slice("创建上下文的BeanFactory,指明使用的就是 DefaultListableBeanFactory ","DefaultListableBeanFactory beanFactory = createBeanFactory();");
        //...
        Code.slice("真正的开始去加载bean，对应不同的场景，这里有可能是xml的方式加载，也有可能是注解的方式加载,下面以xml为例", "loadBeanDefinitions(beanFactory);");
        //...
    }

    @Trace(
            index = 3,
            originClassName = "org.springframework.context.support.XmlWebApplicationContext",
            introduction = "这里仅仅只看xml的实现方式",
            function = "protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException"
    )
    public void loadBeanDefinitions(){
        //...
        Code.slice("beanFactory作为registry被XmlReader持有,后续在bean注册的时候就会用到","XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);");
        //...
        Code.slice("在内部通过reader遍历所有的配置文件地址，加载对应的资源，开始一个个的扫描","loadBeanDefinitions(beanDefinitionReader);");
        //...
    }

    @Trace(
            index = 4,
            originClassName = "org.springframework.beans.factory.xml.XmlBeanDefinitionReader",
            function = "protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)throws BeanDefinitionStoreException"

    )
    public void doLoadBeanDefinitions(){
        //...
        Code.slice("找到对应的xml资源后,解析出来dom结构","Document doc = doLoadDocument(inputSource, resource);");
        //...
        Code.slice("里面开始从document的根开始,使用BeanDefinitionParserDelegate作为代理，识别读到的节点的名字，比如读到的是<bean>标签，则按照它的方式来处理","int count = registerBeanDefinitions(doc, resource);");
        //...
    }

    @Trace(
            index = 5,
            originClassName = "org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader",
            function = "protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate)"
    )
    @Recall(
            traceIndex = 3,
            tip = "getReaderContext().getRegistry() 它实际上就是上文提到过的 beanFactory"
    )
    public void processBeanDefinition(){
        Code.slice("将读取到的xml标签的内容解析成类GenericBeanDefinition，并放入BeanDefinitionHolder保管","BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);");
        //...
        Code.slice("读取到的内容正式的注册到BeanFactory,也就是进入DefaultListableBeanFactory来执行注册详情","BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());");
        //...
    }

    @Trace(
            index = 6,
            originClassName = "org.springframework.beans.factory.support.DefaultListableBeanFactory",
            function = "public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionStoreException"
    )
    @KeyPoint
    public void registerBeanDefinition(){
       //...
       Code.slice("读取到的bean放入到map,也就是说只要beanName不一样，就可以保留下来","this.beanDefinitionMap.put(beanName, beanDefinition);");
       Code.slice("记住所有的bean的名字，后面用到","this.beanDefinitionNames.add(beanName);");
       //...
    }

    @Trace(
            index = 7,
            originClassName = "org.springframework.context.support.AbstractApplicationContext",
            function = "protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) "
    )
    public void finishBeanFactoryInitialization(){
        //...
        Code.slice("此时bean已经记在完毕，开始去初始化所有非懒加载的 bean","beanFactory.preInstantiateSingletons();");
    }

    @Trace(
            index = 8,
            originClassName = "org.springframework.beans.factory.support.DefaultListableBeanFactory",
            function = "public void preInstantiateSingletons() throws BeansException"
    )
    public void preInstantiateSingletons(){
        //...
        Code.slice("遍历所有bean的名字，找到满足单例(就是读取scope上标明是singleton或者是没有写scope)的bean，开始生成","getBean(beanName);");
    }
    
    @Trace(
            index = 9,
            originClassName = "org.springframework.beans.factory.support.AbstractBeanFactory",
            function = "protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException"
    )
    public void doGetBean(){
        //...
        Code.slice("根据定义，如果他是singleton，就执行对应的逻辑",
                "if (mbd.isSingleton()) {\n" +
                "   sharedInstance = getSingleton(beanName, () -> {\n" +
                "     return createBean(beanName, mbd, args);\n" +
                "   });\n" +
                "}");
        //...
    }
    @Trace(
            index = 10,
            originClassName = "org.springframework.beans.factory.support.DefaultSingletonBeanRegistry",
            function = "public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory)"
    )
    @Recall(
            traceIndex = 9,
            tip = "singletonFactory.getObject()执行的方法即上面提到的 createBean,创建的底层逻辑实际上也就是使用反射来创建的"
    )
    @KeyPoint
    public void getSingleton(){
        Code.slice("单例获取是同步进行的","synchronized (this.singletonObjects) ");
        //...
        Code.slice("打上标记，表明单例已经开始创建，保证后来的方法不会重复创建","beforeSingletonCreation(beanName);");
        //...
        Code.slice("开始执行获取对象，这里利用了java8的lambda表达式,创建","singletonObject = singletonFactory.getObject();");
        //...
        Code.slice("创建好的对象保存起来，通过其他方式，比如beanName来获取单例的时候，就查到的是已经创建的唯一一个bean","addSingleton(beanName, singletonObject);");
        //...
    }

}
