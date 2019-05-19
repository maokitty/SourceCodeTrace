package paxi.maokitty.source.spring;

import paxi.maokitty.source.annotation.*;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/5/19.
 */
@Background(
        target = "了解spring中事务具体实现的原理",
        conclusion = "1:传播机制为spring自己的逻辑，控制多层事务嵌套的处理逻辑;2:spring具体的执行事务以它自己的DataSourceTransactionManager 为例，本质上还是java的Connection来最终执行操作",
        sourceCodeProjectName = "spring-framework",
        sourceCodeAddress = "https://github.com/spring-projects/spring-framework",
        projectVersion = "5.1.1.RELEASE"
)
public class TransactionHandleInSpring {
    @Main
    @Trace(
            index = 0,
            originClassName = "org.springframework.transaction.interceptor.TransactionAspectSupport",
            function = "protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,final InvocationCallback invocation) throws Throwable",
            more = "paxi.maokitty.source.spring.TransactionAnnotationUseInSpring 可以看到使用注解之后最终执行的代码在这里"
    )
    public void invokeWithinTransaction(){
        //...
        Code.SLICE.source("final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);")
                .interpretation("查到对应方法的事务配置");
        Code.SLICE.source("final PlatformTransactionManager tm = determineTransactionManager(txAttr);")
                .interpretation("拿到transactionManager,比如用户在xml中配置的 org.springframework.jdbc.datasource.DataSourceTransactionManager,以下分析以它为例");
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

    @Trace(
            index = 1,
            originClassName = "org.springframework.transaction.interceptor.TransactionAspectSupport",
            function = "protected TransactionInfo createTransactionIfNecessary(@Nullable PlatformTransactionManager tm,@Nullable TransactionAttribute txAttr, final String joinpointIdentification)"
    )
    public  void createTransactionIfNecessary(){
        //...
        Code.SLICE.source("status = tm.getTransaction(txAttr);")
                .interpretation("根据用户配置的事务传播机制，返回对应的事务对象");
        //...
        Code.SLICE.source("return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);")
                .interpretation("将所有的事务信息封装到 TransactionInfo ,并在建立它与当前线程的关系，存在ThreadLocal对象transactionInfoHolder中 ");
    }

    @Trace(
            index = 2,
            originClassName = "org.springframework.transaction.support.AbstractPlatformTransactionManager",
            function = "public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition) throws TransactionException"
    )
    @KeyPoint(
        desc = "1:连接和线程存在联系，同一个线程使用的是同一个连接" +
                "2:第一个事务的处理逻辑，" +
                "PROPAGATION_MANDATORY 必须有事务，抛出异常" +
                "PROPAGATION_REQUIRED/PROPAGATION_REQUIRES_NEW/PROPAGATION_NESTED 新建事务，与当前线程建立联系" +
                "PROPAGATION_SUPPORTS/PROPAGATION_NEVER/PROPAGATION_NOT_SUPPORTED 由于是第一个支持对应的语义，不创建事务"
    )
    public void getTransaction(){
        Code.SLICE.source("Object transaction = doGetTransaction();")
                .interpretation("对于Spring的DataSourceTransactionManager,它就是新建一个 DataSourceTransactionObject 对象，持有ConnectionHolder")
                .interpretation("1：它的连接对象是从本地线程获取的，也就是说，如果之前在这个线程上执行过事务，那么这里就会复用同一个连接");
        //...
        Code.SLICE.source("if (isExistingTransaction(transaction)) {\n" +
                "   // Existing transaction found -> check propagation behavior to find out how to behave.\n" +
                "   return handleExistingTransaction(definition, transaction, debugEnabled);\n" +
                "  }")
        .interpretation("如果当前事务执行的上下文还有事务，就按照有事务的逻辑处理,否则继续往下执行")
        .interpretation("1:这里的判断标识就是去看ConnectionHolder之前是否有只有过连接，并且连接仍然活着 ");
        //...
        Code.SLICE.source("if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {\n" +
                "   throw new IllegalTransactionStateException(\n" +
                "     \"No existing transaction found for transaction marked with propagation 'mandatory'\");\n" +
                "  }").interpretation("当前位置必须包含事务，没有事务就抛出异常");
        Code.SLICE.source("else if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||\n" +
                "    definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||\n" +
                "    definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {\n" +
                "   SuspendedResourcesHolder suspendedResources = suspend(null);\n" +
                "   if (debugEnabled) {\n" +
                "    logger.debug(\"Creating new transaction with name [\" + definition.getName() + \"]: \" + definition);\n" +
                "   }\n" +
                "   try {\n" +
                "    boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);\n" +
                "    DefaultTransactionStatus status = newTransactionStatus(\n" +
                "      definition, transaction, true, newSynchronization, debugEnabled, suspendedResources);\n" +
                "    doBegin(transaction, definition);\n" +
                "    prepareSynchronization(status, definition);\n" +
                "    return status;\n" +
                "   }\n" +
                "   catch (RuntimeException | Error ex) {\n" +
                "    resume(null, suspendedResources);\n" +
                "    throw ex;\n" +
                "   }\n" +
                "  }").interpretation("处理第一次碰到这三种传播类型的事务")
                .interpretation("1:第一次执行，不会执行任何的挂起操作")
                .interpretation("2:新建DefaultTransactionStatus 执有所有 AbstractPlatformTransactionManager 所需要的内容，它用来表示一个当前的 事务对象")
                .interpretation("3:初始化连接，比如：从数据库获取连接,将连接的自动提交设置成 false，详见 @Trace{index=3}")
                .interpretation("4:在 TransactionSynchronizationManager 中建立连接和当前线程的关系，比如:" +
                        "标记当前线程有正在运行的事务，存储在actualTransactionActive中；" +
                        "设置当前线程运行事务的隔离级别，存在 currentTransactionIsolationLevel中;" +
                        "设置当前线程正在运行事务的 read-only标记，存在 currentTransactionReadOnly 中" +
                        "存储当前线程的名字" +
                        "在synchronizations中插入一个空的set,表明它是第一个在当前线程执行的事务。同步机制即如果需要挂起当前正在执行的事务则放入这里，等优先的事务执行完后再恢复它并执行它")
                .interpretation("返回新建的事务对象 DefaultTransactionStatus");

         //...
        Code.SLICE.source("return prepareTransactionStatus(definition, null, true, newSynchronization, debugEnabled, null);")
                .interpretation("能执行到这里，可能的传播机制是:PROPAGATION_SUPPORTS/PROPAGATION_NEVER/PROPAGATION_NOT_SUPPORTED 这里实际上是没有创建一个有效的事务，但仍然在同步机制上表示可能的同步机制");
        
    }

    @Trace(
            index = 3,
            originClassName = "org.springframework.jdbc.datasource.DataSourceTransactionManager",
            function = "protected void doBegin(Object transaction, TransactionDefinition definition)"
    )
    public void doBegin(){
       //...
       Code.SLICE.source("Connection newCon = this.dataSource.getConnection();")
               .interpretation("从数据库获取连接");
        // ...
        Code.SLICE.source("txObject.setConnectionHolder(new ConnectionHolder(newCon), true);")
                .interpretation("1.将连接保存在DataSourceTransactionObject中")
                .interpretation("2:表明这是一个新的连接");
        Code.SLICE.source("txObject.getConnectionHolder().setSynchronizedWithTransaction(true);")
                .interpretation("标记字段 SynchronizedWithTransaction 为true");
        //...
        Code.SLICE.source("Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);")
                .interpretation("根据方法的配置设置连接的隔离级别");
        //...
        Code.SLICE.source("con.setAutoCommit(false);")
                .interpretation("设置连接为不自动提交");
        //...
        Code.SLICE.source("txObject.getConnectionHolder().setTransactionActive(true);")
                .interpretation("标记连接现在是存活的");
        //...
        Code.SLICE.source("TransactionSynchronizationManager.bindResource(getDataSource(), txObject.getConnectionHolder());")
                .interpretation("建立当前连接和线程的关系,即存储到 ThreadLocal对象TransactionSynchronizationManager的resources中 ");
        //...
    }
    
    

    @Trace(
            index = 4,
            originClassName = "org.springframework.transaction.support.AbstractPlatformTransactionManager",
            function = "private TransactionStatus handleExistingTransaction(TransactionDefinition definition, Object transaction, boolean debugEnabled) throws TransactionException"
    )
    @KeyPoint(
            desc = "嵌套事务语义的实现。" +
                    "PROPAGATION_NEVER 抛出异常;" +
                    "PROPAGATION_NOT_SUPPORTED 挂起当前线程事务，当前线程此时并不会携带任何事务" +
                    "PROPAGATION_REQUIRES_NEW 挂起当前线程事务，并将新建的事务与当前线程建立联系" +
                    "PROPAGATION_NESTED 挂起当前事务，默认创建安全点，并未挂起当前线程事务" +
                    "PROPAGATION_REQUIRED/PROPAGATION_SUPPORTS/PROPAGATION_MANDATORY 则是在当前事务中继续执行"
    )
    public void handleExistingTransaction(){
        Code.SLICE.source(" if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NEVER) {\n" +
                "   throw new IllegalTransactionStateException(\n" +
                "     \"Existing transaction found for transaction marked with propagation 'never'\");\n" +
                "  }").source("看spring的自定义传播类型，如果方法标注的是不能有事务，那么扔出异常");

        Code.SLICE.source("if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NOT_SUPPORTED) {\n" +
                "   if (debugEnabled) {\n" +
                "    logger.debug(\"Suspending current transaction\");\n" +
                "   }\n" +
                "   Object suspendedResources = suspend(transaction);\n" +
                "   boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);\n" +
                "   return prepareTransactionStatus(\n" +
                "     definition, null, false, newSynchronization, debugEnabled, suspendedResources);\n" +
                "  }")
                .interpretation("当前方法包含的传播类型为不支持的事务。")
                .interpretation("1:当前线程关联的事务统统与线程解除联系，放在返回的 suspendedResources 中 ")
                .interpretation("2:仍然建立一个事务对象返回，用来保存刚和线程解除联系的对象，当前线程此时会被标记成没有事务");

        Code.SLICE.source("if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {\n" +
                "   if (debugEnabled) {\n" +
                "    logger.debug(\"Suspending current transaction, creating new transaction with name [\" +\n" +
                "      definition.getName() + \"]\");\n" +
                "   }\n" +
                "   SuspendedResourcesHolder suspendedResources = suspend(transaction);\n" +
                "   try {\n" +
                "    boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);\n" +
                "    DefaultTransactionStatus status = newTransactionStatus(\n" +
                "      definition, transaction, true, newSynchronization, debugEnabled, suspendedResources);\n" +
                "    doBegin(transaction, definition);\n" +
                "    prepareSynchronization(status, definition);\n" +
                "    return status;\n" +
                "   }\n" +
                "   catch (RuntimeException | Error beginEx) {\n" +
                "    resumeAfterBeginException(transaction, suspendedResources, beginEx);\n" +
                "    throw beginEx;\n" +
                "   }\n" +
                "  }\n")
                .interpretation("每次新建一个事务对象，提现每次新建的语义")
                .interpretation("1:解除前面与线程建立连接的事务")
                .interpretation("2:建立新的事务，存储之前解除的事务")
                .interpretation("3:将新建的事务与当前线程建立联系")
                .interpretation("4:如果在这个新建过程中出现异常，则是将刚解除联系的对象再次绑定到当前线程上去，也就是执行解除关系的逆过程");

        Code.SLICE.source("if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {\n" +
                "   if (!isNestedTransactionAllowed()) {\n" +
                "    throw new NestedTransactionNotSupportedException(\n" +
                "      \"Transaction manager does not allow nested transactions by default - \" +\n" +
                "      \"specify 'nestedTransactionAllowed' property with value 'true'\");\n" +
                "   }\n" +
                "   if (debugEnabled) {\n" +
                "    logger.debug(\"Creating nested transaction with name [\" + definition.getName() + \"]\");\n" +
                "   }\n" +
                "   if (useSavepointForNestedTransaction()) {\n" +
                "    // Create savepoint within existing Spring-managed transaction,\n" +
                "    // through the SavepointManager API implemented by TransactionStatus.\n" +
                "    // Usually uses JDBC 3.0 savepoints. Never activates Spring synchronization.\n" +
                "    DefaultTransactionStatus status =\n" +
                "      prepareTransactionStatus(definition, transaction, false, false, debugEnabled, null);\n" +
                "    status.createAndHoldSavepoint();\n" +
                "    return status;\n" +
                "   }\n" +
                "   else {\n" +
                "    // Nested transaction through nested begin and commit/rollback calls.\n" +
                "    // Usually only for JTA: Spring synchronization might get activated here\n" +
                "    // in case of a pre-existing JTA transaction.\n" +
                "    boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);\n" +
                "    DefaultTransactionStatus status = newTransactionStatus(\n" +
                "      definition, transaction, true, newSynchronization, debugEnabled, null);\n" +
                "    doBegin(transaction, definition);\n" +
                "    prepareSynchronization(status, definition);\n" +
                "    return status;\n" +
                "   }\n" +
                "  }")
                .interpretation("执行嵌套语义,默认创建安全点")
                .interpretation("1:首先要执行嵌套语义，程序必须显示支持的")
                .interpretation("2:默认来说会去创建一个安全点，也就是创建好事务对象后，立马创建一个安全点，创建本身即是拿到连接本身来创建 return getConnection().setSavepoint(SAVEPOINT_NAME_PREFIX + this.savepointCounter);")
                .interpretation("3:不需要创建安全点则只需要按照正常的步骤关联对象到当前线程即可");
        //...

        Code.SLICE.source(" boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);\n" +
                "  return prepareTransactionStatus(definition, transaction, false, newSynchronization, debugEnabled, null);")
                .interpretation(" 这里是仅创建一个新的事务对象,能到达这里的事务类型包括,PROPAGATION_REQUIRED/PROPAGATION_SUPPORTS/PROPAGATION_MANDATORY，意味着直接在当前线程事务里面执行");
    }

    @Trace(
            index = 5,
            originClassName = "org.springframework.transaction.support.AbstractPlatformTransactionManager",
            function = "protected final SuspendedResourcesHolder suspend(@Nullable Object transaction) throws TransactionException"
    )
    @Recall(
       traceIndex = 2,
            tip = "查看传播方式为TransactionDefinition.PROPAGATION_REQUIRED的逻辑"
            
    )
    public void suspend(){
        Code.SLICE.source("if (TransactionSynchronizationManager.isSynchronizationActive()) {")
                .interpretation("判断当前线程是不是已经存放过 synchronizs标识，这种情况的出现一般是存在嵌套的事务标识，第二次进来就会执行这里的逻辑");
        Code.SLICE.source("List<TransactionSynchronization> suspendedSynchronizations = doSuspendSynchronization();")
                .interpretation("获取当前线程存储的 TransactionSynchronization ，如果存在就依次执行 suspend 方法，然后清空当前线程的synchronizations ");
        //...
        Code.SLICE.source("suspendedResources = doSuspend(transaction);")
                .interpretation("清空当前线程的连接,并解除其与当前线程的关系，并进一步的清除 TransactionSynchronizationManager 中设定的所有之前设定的连接相关的值");
        //...
        Code.SLICE.source("return new SuspendedResourcesHolder(\n" +
                "      suspendedResources, suspendedSynchronizations, name, readOnly, isolationLevel, wasActive)")
                .interpretation("清除的信息全部保存到 SuspendedResourcesHolder 已被后续唤醒的时候用");
        //...
        Code.SLICE.source("else if (transaction != null)")
                .interpretation("这一段仍然有线程，但是连接已经关闭了，只需要清除连接和线程关系即可，另外如果连事务对象都没有，就直接返回即可");
        //...
    }
    @Trace(
            index = 6,
            originClassName = "org.springframework.transaction.interceptor.TransactionAspectSupport",
            function = "protected void completeTransactionAfterThrowing(@Nullable TransactionInfo txInfo, Throwable ex)"
    )
    public void completeTransactionAfterThrowing(){
      //...
      Code.SLICE.source("if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) ")
              .interpretation("判断当前抛出的异常是否应该实行回滚");
      //...
      Code.SLICE.source("txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());")
              .interpretation("如果异常需要执行回滚，获取用户配置的TransactionManger(org.springframework.jdbc.datasource.DataSourceTransactionManager)以及要回滚的事务的具体信息来执行回滚");
        
    }
    @Trace(
            index = 7,
            originClassName = "org.springframework.transaction.support.AbstractPlatformTransactionManager",
            function = "private void processRollback(DefaultTransactionStatus status, boolean unexpected)"
    )
    public void processRollback(){
      //...
        Code.SLICE.source("triggerBeforeCompletion(status);")
                .interpretation("如果当前事务是为他新建的一个连接，如果发现这个连接已经没有引用了，那么直接关掉连接，并解除与当前线程的关系");
       Code.SLICE.source("\n" +
               "    if (status.hasSavepoint()) {\n" +
               "     if (status.isDebug()) {\n" +
               "      logger.debug(\"Rolling back transaction to savepoint\");\n" +
               "     }\n" +
               "     status.rollbackToHeldSavepoint();\n" +
               "    }")
               .interpretation("如果当前事务创建过安全点,则回滚到对应的安全点，具体执行实际也是通过 Connection来执行 conHolder.getConnection().rollback((Savepoint) savepoint);");

        Code.SLICE.source("else if (status.isNewTransaction()) {\n" +
                "     if (status.isDebug()) {\n" +
                "      logger.debug(\"Initiating transaction rollback\");\n" +
                "     }\n" +
                "     doRollback(status);\n" +
                "    }")
                .interpretation("如果它是第一层的事务，直接执行回滚,实质上也是执行 con.rollback(); 如果有参与了其它的事务，则有可能是让原有的事务来决定是否执行回滚");
        //...
        Code.SLICE.source("triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);")
                .interpretation("执行对应的清理操作，以及释放连接");
        //...
        Code.SLICE.source("cleanupAfterCompletion(status);")
                .interpretation("清理线程中已经回滚的这个事务的信息，如果有被挂起的事务，恢复他们与当前线程的关系,以便后续的执行");
    }
    @Trace(
            index = 8,
            originClassName = "org.springframework.transaction.interceptor.TransactionAspectSupport",
            function = "protected void commitTransactionAfterReturning(@Nullable TransactionInfo txInfo) "
    )
    public void commitTransactionAfterReturning(){
        Code.SLICE.source("txInfo.getTransactionManager().commit(txInfo.getTransactionStatus())")
            .interpretation("执行完用户方法之后，不需要回滚，则执行提交");
    }
    
     @Trace(
            index = 9,
            originClassName = "org.springframework.transaction.support.AbstractPlatformTransactionManager",
            function = "private void processCommit(DefaultTransactionStatus status) throws TransactionException"
    )
    public void processCommit(){
         //...
        Code.SLICE.source("else if (status.isNewTransaction()) {\n" +
                "     if (status.isDebug()) {\n" +
                "      logger.debug(\"Initiating transaction commit\");\n" +
                "     }\n" +
                "     unexpectedRollback = status.isGlobalRollbackOnly();\n" +
                "     doCommit(status);\n" +
                "    }")
            .interpretation("事务最终执行提交，实际也是获取连接来提交 con.commit();");
         //...
    }
}
