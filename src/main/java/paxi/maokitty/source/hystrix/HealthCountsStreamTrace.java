package paxi.maokitty.source.hystrix;

import paxi.maokitty.source.annotation.*;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/5/30.
 */
@Background(
        target = "了解Hystrix的HealthCounts是怎么做的",
        conclusion = "Hystrix底层依赖RxJava,通过RxJava的语义，实现将一个个的命令执行结果分成桶存储，然后每个桶又通过时间窗口的聚合，算出错误占比，然后在每次执行前判断错误占比是否是继续执行用户的 run/constructor方法还是 执行 getFallBack",
        sourceCodeProjectName = "Hystrix",
        sourceCodeAddress = "https://github.com/Netflix/Hystrix",
        projectVersion = "1.5.18"

)
public class HealthCountsStreamTrace {
    @Main
    @Trace(
            index = 0,
            originClassName = "com.netflix.hystrix.metric.consumer.HealthCountsStream",
            function = "public static HealthCountsStream getInstance(HystrixCommandKey commandKey, int numBuckets, int bucketSizeInMs)",
            introduction = "给定的command都会维护一个滑动的监控计数流,在这个流里面有一个滑动窗口的抽象。滑动窗口的大小为 metricsHealthSnapshotIntervalInMilliseconds 配置的值,记为t1" +
                    "它内部包含有 metricsRollingStatisticalWindowBuckets 个桶,记为b ,那么每经过 t1/b 毫秒就会新产生一个桶"
    )
    public void getInstance(){
        //...
        Code.SLICE.source("  HealthCountsStream newStream = new HealthCountsStream(commandKey, numBuckets, bucketSizeInMs,HystrixCommandMetrics.appendEventToBucket);")
                .interpretation("每个command名相同的，都会使用同一个HealthCountsStream，如果没有就新建1个")
                .interpretation("1:commandKey 就是用户通过 HystrixCommandKey.Factory.asKey(\"commandKey\")) 配置的")
                .interpretation("2: numBuckets 就是一个滑动窗口里面有几个桶")
                .interpretation("3:bucketSizeInMs 就是整个滑动窗口的大小")
                .interpretation("4:HystrixCommandMetrics.appendEventToBucket 自定义的函数，作用是把命令的执行结果存放到桶中")
                ;
        //...
        Code.SLICE.source("healthStream.startCachingStreamValuesIfUnstarted();")
                .interpretation("订阅健康计算");
        //...
    }
    @Trace(
            index = 1,
            originClassName = "com.netflix.hystrix.metric.consumer.HealthCountsStream",
            function = "private HealthCountsStream(final HystrixCommandKey commandKey, final int numBuckets, final int bucketSizeInMs Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion)",
            introduction = "构造函数"
    )
    public void  HealthCountsStreamConstructor(){
        Code.SLICE.source(" super(HystrixCommandCompletionStream.getInstance(commandKey), numBuckets, bucketSizeInMs, reduceCommandCompletion, healthCheckAccumulator);")
                .interpretation("1:HystrixCommandCompletionStream 命令执行完的时候，会发送变化给它，通过writeOnlyObject传递到readOnlyObject订阅的消费者")
                .interpretation("2:healthCheckAccumulator 就是承担着去计算指标的函数,明确的算出成功了多少，失败了多少，并算出失败的比例")
                ;
    }

    @Trace(
            index = 2,
            originClassName = "com.netflix.hystrix.metric.consumer.BucketedRollingCounterStream",
            function = "protected BucketedRollingCounterStream(HystrixEventStream<Event> stream, final int numBuckets, int bucketSizeInMs,\n"+
                "final Func2<Bucket, Event, Bucket> appendRawEventToBucket,\n"+
                "final Func2<Output, Bucket, Output> reduceBucket)",
            introduction = "HealthCountsStream的父构造函数"
    )
    @KeyPoint
    public void  BucketedRollingCounterStreamConstrutor(){
        Code.SLICE.source("super(stream, numBuckets, bucketSizeInMs, appendRawEventToBucket);")
                .interpretation("构造每个桶的可观察的对象，以便计算每个桶内的指标");

        Code.SLICE.source(" Func1<Observable<Bucket>, Observable<Output>> reduceWindowToSummary = new Func1<Observable<Bucket>, Observable<Output>>() {\n" +
                "            @Override\n" +
                "            public Observable<Output> call(Observable<Bucket> window) {\n" +
                "                return window.scan(getEmptyOutputValue(), reduceBucket).skip(numBuckets);\n" +
                "            }\n" +
                "        };")
                .interpretation("负责将时间窗口内的所有桶的结果全部计算出来，算出在这个时间窗口内，有多少处理成功，有多少处理失败,失败的比例是多少")
                .interpretation("1:scan表示对发过来的每一项都应用reduceBucket，reduceBucket即healthCheckAccumulator，一个窗口会同时发出来多个桶的数据，因而需要算出每一个桶的成功失败数")
                .interpretation("2:当计算完成每一个桶的结果之后，开始与下一个桶的结果进行合并计算，这些合并计算的都是中间结果，不需要向后面传递，因此省略掉前面的桶数的计算，返回最终汇总的结果")
                ;
        Code.SLICE.source(" this.sourceStream = bucketedStream      //stream broken up into buckets\n" +
                "                .window(numBuckets, 1)          //emit overlapping windows of buckets\n" +
                "                .flatMap(reduceWindowToSummary) //convert a window of bucket-summaries into a single summary\n" +
                "                .doOnSubscribe(new Action0() {\n" +
                "                    @Override\n" +
                "                    public void call() {\n" +
                "                        isSourceCurrentlySubscribed.set(true);\n" +
                "                    }\n" +
                "                })\n" +
                "                .doOnUnsubscribe(new Action0() {\n" +
                "                    @Override\n" +
                "                    public void call() {\n" +
                "                        isSourceCurrentlySubscribed.set(false);\n" +
                "                    }\n" +
                "                })\n" +
                "                .share()                        //multiple subscribers should get same data\n" +
                "                .onBackpressureDrop();          //if there are slow consumers, data should not buffer")
                .interpretation("对整个时间窗口的数据做处理")
                .interpretation("1:bucketedStream已经将每个窗口内的执行结果放入了一个桶里面")
                .interpretation("2:总共统计桶的数量为配置值算出来的numBuckets，如果总共窗口里面缓存的到了配置的量，就扔掉最开始创建的那个 ")
                .interpretation("3:汇总出时间窗口内的执行结果")
                .interpretation("4:记下当前被观察者是否已经有订阅或者没有订阅")
                .interpretation("5:确保所有订阅者都拿到一样的结果")
                .interpretation("6:命令执行过快，可能导致计算处理不过来，这里的处理措施就是，根本不缓存执行的数据，只有在消费的时候，拿取后面产生的最新的执行结果（Backpressure）")
                ;
    }

    @Trace(
            index = 3,
            originClassName = "com.netflix.hystrix.metric.consumer.BucketedCounterStream",
            function = " protected BucketedCounterStream(final HystrixEventStream<Event> inputEventStream, final int numBuckets, final int bucketSizeInMs,\n" +
                        " final Func2<Bucket, Event, Bucket> appendRawEventToBucket)",
            introduction = "BucketedRollingCounterStream的父构造函数"
    )
    @Recall(
            traceIndex = 1,
            tip = "appendEventToBucket，inputEventStream即readOnlyStream"
    )
    @KeyPoint
    public void BucketedCounterStreamConstrutor(){
        //...
        Code.SLICE.source(" this.reduceBucketToSummary = new Func1<Observable<Event>, Observable<Bucket>>() {\n" +
                "            @Override\n" +
                "            public Observable<Bucket> call(Observable<Event> eventBucket) {\n" +
                "                return eventBucket.reduce(getEmptyBucketSummary(), appendRawEventToBucket);\n" +
                "            }\n" +
                "        };")
                .interpretation("event对应的是传入的参数，bucket即返回值，也就是根据hystrix产生的事件，对应的将健康检查的返回结果放入桶中,所谓的桶其实也就是一个long类型的数组，每个位置存储的值都有对应的含义")
                .interpretation("1:getEmptyBucketSummary对于HealthCountsStream来说就是一个空的数组")
                .interpretation("2:appendRawEventToBucket就是就是appendEventToBucket，它的作用就是把每次执行的结果都按照顺序扔到桶里面去，相当于是创建了一个桶");
        //...
        Code.SLICE.source(" this.bucketedStream = Observable.defer(new Func0<Observable<Bucket>>() {\n" +
                "            @Override\n" +
                "            public Observable<Bucket> call() {\n" +
                "                return inputEventStream\n" +
                "                        .observe()\n" +
                "                        .window(bucketSizeInMs, TimeUnit.MILLISECONDS) //bucket it by the counter window so we can emit to the next operator in time chunks, not on every OnNext\n" +
                "                        .flatMap(reduceBucketToSummary)                //for a given bucket, turn it into a long array containing counts of event types\n" +
                "                        .startWith(emptyEventCountsToStart);           //start it with empty arrays to make consumer logic as generic as possible (windows are always full)\n" +
                "            }\n" +
                "        });")
                .interpretation("bucketedStream被订阅的时候，开始产生作用")
                .interpretation("1:将传过来的数据按照计算好的 时间 在时间窗口里面缓冲")
                .interpretation("2:将时间窗口内的所有执行结果统一放入一个桶内")
                .interpretation("3:在真正的发送数据前，先发送一个空的数组集合，保证消费者的逻辑尽可能的通用")
                ;
    }

    @Trace(
            index = 4,
            originClassName = "com.netflix.hystrix.metric.consumer.BucketedCounterStream",
            function = "public void startCachingStreamValuesIfUnstarted()",
            more = "至此HealthCountsStream的初始化结束"
    )
    @Recall(
            traceIndex = 2,
            tip = "observe()获取的就是healthCount已经规划好的 sourceStream,最整个桶做计算"
    )
    public void startCachingStreamValuesIfUnstarted(){
        //...
        Code.SLICE.source("Subscription candidateSubscription = observe().subscribe(counterSubject);")
                .interpretation("开始订阅被观察者counterSubject是一个BehaviorSubject");
         //...
    }
    @Trace(
            index = 5,
            originClassName = "com.netflix.hystrix.HystrixCommand",
            function = "public Future<R> queue()",
            more = "开始执行Hystrix的命令"
    )
    public void queue(){
        Code.SLICE.source("final Future<R> delegate = toObservable().toBlocking().toFuture();")
                .interpretation("hystrix内部使用到了自己的线程池来异步执行用户的自己的方法，因而这里转而使用Future来执行回调，核心的业务逻辑则放在了toObservable里头。另外toFuture也实现了对Observable的订阅");
        //...
    }

    @Trace(
            index = 6,
            originClassName = "com.netflix.hystrix.AbstractCommand",
            function = "public Observable<R> toObservable()"
    )
    public void toObservable(){
        //...
        Code.SLICE.source(" final Action0 terminateCommandCleanup = new Action0() {\n" +
                "\n" +
                "            @Override\n" +
                "            public void call() {\n" +
                "                if (_cmd.commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED, CommandState.TERMINAL)) {\n" +
                "                    handleCommandEnd(false); //user code never ran\n" +
                "                } else if (_cmd.commandState.compareAndSet(CommandState.USER_CODE_EXECUTED, CommandState.TERMINAL)) {\n" +
                "                    handleCommandEnd(true); //user code did run\n" +
                "                }\n" +
                "            }\n" +
                "        };")
                .interpretation("定义如果执行\"用户的方法\"完成的执行逻辑");
        //...
        Code.SLICE.source("final Func0<Observable<R>> applyHystrixSemantics = new Func0<Observable<R>>() {\n" +
                "            @Override\n" +
                "            public Observable<R> call() {\n" +
                "                if (commandState.get().equals(CommandState.UNSUBSCRIBED)) {\n" +
                "                    return Observable.never();\n" +
                "                }\n" +
                "                return applyHystrixSemantics(_cmd);\n" +
                "            }\n" +
                "        };")
                .interpretation("构建Hystrix的语义处理逻辑函数，这是核心的实现Hystrix逻辑的地方");
        //...
        Code.SLICE.source("return Observable.defer(" +
                "...." +
                "Observable<R> hystrixObservable =\n" +
                "                        Observable.defer(applyHystrixSemantics)" +
                "..." +
                "   return afterCache\n" +
                "                        .doOnTerminate(terminateCommandCleanup)     // perform cleanup once (either on normal terminal state (this line), or unsubscribe (next line))\n" +
                "...)")
                .interpretation("最终创建Observable对象，关联对应的操作动作。创建方式为 defer,另外在执行之前，如果缓存命中会首先走缓存的逻辑(本次分析以实际执行为主，缓存略)");
    }
    @Trace(
            index = 7,
            originClassName = "com.netflix.hystrix.AbstractCommand",
            function = "private Observable<R> applyHystrixSemantics(final AbstractCommand<R> _cmd) "
    )
    public void applyHystrixSemantics(){
        //...
        Code.SLICE.source("if (circuitBreaker.allowRequest()){")
                  .interpretation("先判断断路器是否允许请求，如果不允许，则直接执行短路逻辑，调用Fallback，详见handleShortCircuitViaFallback ");
        Code.SLICE.source("     final TryableSemaphore executionSemaphore = getExecutionSemaphore();")
                  .interpretation("根据用户的配置，获取允许的请求令牌数，即通过 executionIsolationSemaphoreMaxConcurrentRequests 配置的参数 ");
        //...
        Code.SLICE.source("     if (executionSemaphore.tryAcquire()) {")
                  .interpretation("获取令牌，如果不允许执行，则调用 handleSemaphoreRejectionViaFallback");
        //...
        Code.SLICE.source("            return executeCommandAndObserve(_cmd)")
                .interpretation("执行用户自己的逻辑");
        //...
        Code.SLICE.source("     return handleSemaphoreRejectionViaFallback();")
                .interpretation("获取令牌失败的执行逻辑");
        //...
        Code.SLICE.source("return handleShortCircuitViaFallback();")
                .interpretation("不允许执行直接走断路逻辑");
        //...
    }

    @Trace(
            index = 8,
            originClassName = "com.netflix.hystrix.HystrixCircuitBreakerImpl",
            function = "public boolean allowRequest()"
    )
    public void allowRequest(){
        Code.SLICE.source("if (properties.circuitBreakerForceOpen().get()) {\n" +
                "                // properties have asked us to force the circuit open so we will allow NO requests\n" +
                "                return false;\n" +
                "            }\n")
                .interpretation("获取 HystrixCommandProperties.Setter.withCircuitBreakerForceOpen设置值，看是否要强制使用断路器,如果配置开启，则直接走短路逻辑");
        Code.SLICE.source(" if (properties.circuitBreakerForceClosed().get()) {\n" +
                "                // we still want to allow isOpen() to perform it's calculations so we simulate normal behavior\n" +
                "                isOpen();\n" +
                "                // properties have asked us to ignore errors so we will ignore the results of isOpen and just allow all traffic through\n" +
                "                return true;\n" +
                "            }")
                .interpretation("如果 HystrixCommandProperties.Setter.withCircuitBreakerForceClosed 设置为强制不使用断路器，仍然去模拟正常的表现，但是不管正常数据如何，直接返回执行用户的调用逻辑 run/construct");
        Code.SLICE.source("return !isOpen() || allowSingleTest();")
                .interpretation("没有开关的强制定义，则走正常的判断逻辑")
                .interpretation("1:判断当前的错误量是否已经满足执行短路的条件")
                .interpretation("2:如果决定了开启断路器，执行短路逻辑，看看是否已经短路的时间超过用户定义的一直执行断路器的时间，是否需要再一次执行用户的方法，测试能不能成功");
    }
    @Trace(
            index = 9,
            originClassName = "com.netflix.hystrix.HystrixCircuitBreakerImpl",
            function = "public boolean isOpen() "
    )
    @Recall(
            traceIndex = 4,
            tip = "counterSubject使用"
    )
    public void isOpen(){
        Code.SLICE.source("if (circuitOpen.get()) {\n" +
                "                // if we're open we immediately return true and don't bother attempting to 'close' ourself as that is left to allowSingleTest and a subsequent successful test to close\n" +
                "                return true;\n" +
                "            }")
                .interpretation("如果断路器当前已经开启了，直接返回应该是开启服务的");
        Code.SLICE.source("HealthCounts health = metrics.getHealthCounts();")
                .interpretation("获取健康统计的结果，即获取上述订阅流的counterSubject存储的结果");
        Code.SLICE.source("if (health.getTotalRequests() < properties.circuitBreakerRequestVolumeThreshold().get()) {\n" +
                "                // we are not past the minimum volume threshold for the statisticalWindow so we'll return false immediately and not calculate anything\n" +
                "                return false;\n" +
                "            }")
                .interpretation("如果当前窗口内总的请求量还是少于配置的circuitBreakerRequestVolumeThreshold，断路器仍然关闭，不管错误了多少,仍然执行用户的 run/construct");
        Code.SLICE.source("if (health.getErrorPercentage() < properties.circuitBreakerErrorThresholdPercentage().get()) {\n" +
                "                return false;\n" +
                "            }")
                .interpretation("如果错误的次数百分比比配置的要小，断路器仍然关闭，执行用户的 run/construct方法 （错误比的计算逻辑:(int) ((double) errorCount / totalCount * 100);）");
        Code.SLICE.source("if (circuitOpen.compareAndSet(false, true))")
                .interpretation("执行到这里说明错误比例已经超过了预先设定的值，需要开启断路器,同时记下此时的时间");
        //...
    }
    @Trace(
            index = 10,
            originClassName = "com.netflix.hystrix.HystrixCircuitBreakerImpl",
            function = "public boolean allowSingleTest() "
    )
    public void allowSingleTest(){
        Code.SLICE.source("long timeCircuitOpenedOrWasLastTested = circuitOpenedOrLastTestedTime.get();")
                .interpretation("已经判定了断路器应该开启，此时判断是否需要再次测试一下能够成功执行,首先获取上次断路器开启的时间");
        Code.SLICE.source("if (circuitOpen.get() && System.currentTimeMillis() > timeCircuitOpenedOrWasLastTested + properties.circuitBreakerSleepWindowInMilliseconds().get()) {\n" +
                "                // We push the 'circuitOpenedTime' ahead by 'sleepWindow' since we have allowed one request to try.\n" +
                "                // If it succeeds the circuit will be closed, otherwise another singleTest will be allowed at the end of the 'sleepWindow'.\n" +
                "                if (circuitOpenedOrLastTestedTime.compareAndSet(timeCircuitOpenedOrWasLastTested, System.currentTimeMillis())) {\n" +
                "                    // if this returns true that means we set the time so we'll return true to allow the singleTest\n" +
                "                    // if it returned false it means another thread raced us and allowed the singleTest before we did\n" +
                "                    return true;\n" +
                "                }\n" +
                "            }\n" +
                "            return false;")
                .interpretation("当断路器当前是开启的时候，并且当前时间已经到了用户自定义(circuitBreakerSleepWindowInMilliseconds)的尝试间隔时间，放过这次执行，去尝试一次用户的方法，否则仍然开启断路器");

    }
    @Trace(
            index = 11,
            originClassName = "com.netflix.hystrix.AbstractCommand",
            function = "private Observable<R> executeCommandAndObserve(final AbstractCommand<R> _cmd) "
    )
    public void executeCommandAndObserve(){
        //...
        Code.SLICE.source(" final Func1<Throwable, Observable<R>> handleFallback = new Func1<Throwable, Observable<R>>() {\n" +
                "            @Override\n" +
                "            public Observable<R> call(Throwable t) {\n" +
                "                Exception e = getExceptionFromThrowable(t);\n" +
                "                executionResult = executionResult.setExecutionException(e);\n" +
                "                if (e instanceof RejectedExecutionException) {\n" +
                "                    return handleThreadPoolRejectionViaFallback(e);\n" +
                "                } else if (t instanceof HystrixTimeoutException) {\n" +
                "                    return handleTimeoutViaFallback();\n" +
                "                } else if (t instanceof HystrixBadRequestException) {\n" +
                "                    return handleBadRequestByEmittingError(e);\n" +
                "                } else {\n" +
                "                    /*\n" +
                "                     * Treat HystrixBadRequestException from ExecutionHook like a plain HystrixBadRequestException.\n" +
                "                     */\n" +
                "                    if (e instanceof HystrixBadRequestException) {\n" +
                "                        eventNotifier.markEvent(HystrixEventType.BAD_REQUEST, commandKey);\n" +
                "                        return Observable.error(e);\n" +
                "                    }\n" +
                "\n" +
                "                    return handleFailureViaFallback(e);\n" +
                "                }\n" +
                "            }\n" +
                "        };\n")
                .interpretation("定义当执行用户方法出现问题时的执行逻辑，比如，执行时放回的异常是 HystrixTimeoutException,则去调用 getFallback 方法");
        //...
        Code.SLICE.source("execution = executeCommandWithSpecifiedIsolation(_cmd);")
                .interpretation("根据用户选择的隔离策略来使用不同的执行方式");
        //...
    }
    @Trace(
            index = 12,
            originClassName = "com.netflix.hystrix.AbstractCommand",
            function = "private Observable<R> executeCommandWithSpecifiedIsolation(final AbstractCommand<R> _cmd)"
    )
    private void executeCommandWithSpecifiedIsolation(){
      Code.SLICE.source("if (properties.executionIsolationStrategy().get() == ExecutionIsolationStrategy.THREAD)")
              .interpretation("如果是使用线程池则使用线程池的逻辑，否则走信号量");
        //...
      Code.SLICE.source("       return getUserExecutionObservable(_cmd);")
              .interpretation("执行用户的 run 方法，将结果通过 Observable.just 转成Observable返回");
        //...
        Code.SLICE.source(".subscribeOn(threadPool.getScheduler(new Func0<Boolean>() {\n" +
                "                @Override\n" +
                "                public Boolean call() {\n" +
                "                    return properties.executionIsolationThreadInterruptOnTimeout().get() && _cmd.isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT;\n" +
                "                }\n" +
                "            }));")
                .interpretation("放入自定义的线程池中取执行");
        //...
    }
    @Trace(
            index = 13,
            originClassName = "com.netflix.hystrix.AbstractCommand",
            function = "private void handleCommandEnd(boolean commandExecutionStarted)"
    )
    @Recall(
            traceIndex = 6,
            tip = "完成的时候定义的处理逻辑"
    )
    private void handleCommandEnd(){
      //...
        Code.SLICE.source("metrics.markCommandDone(executionResult, commandKey, threadPoolKey, commandExecutionStarted);")
                .interpretation("更新统计的指标,它会为每个线程定义一个 HystrixThreadEventStream ，然后通过类型不同，使用stream来写入对应的结果");
        //...
    }
    @Trace(
            index = 14,
            originClassName = "com.netflix.hystrix.metric.HystrixThreadEventStream",
            function = "public void executionDone(ExecutionResult executionResult, HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey)"
    )
    public void executionDone(){
        //...
        Code.SLICE.source("HystrixCommandCompletion event = HystrixCommandCompletion.from(executionResult, commandKey, threadPoolKey);\n" +
                "        writeOnlyCommandCompletionSubject.onNext(event);")
                .interpretation("生成一个完成的时间，写入完成的Subject,也就是Observable对象开始发生变化");
    }

    @Trace(
            index = 15,
            originClassName = "com.netflix.hystrix.metric.HystrixThreadEventStream",
            function = "/* package */ HystrixThreadEventStream(Thread thread)"
    )
    public void HystrixThreadEventStreamConstructor(){
        //...
        Code.SLICE.source("writeOnlyCommandCompletionSubject = PublishSubject.create();")
                .interpretation("writeOnlyCommandCompletionSubject本事就是一个PublishSubject");
        //...
        Code.SLICE.source("writeOnlyCommandCompletionSubject\n" +
                "                .onBackpressureBuffer()\n" +
                "                .doOnNext(writeCommandCompletionsToShardedStreams)\n" +
                "                .unsafeSubscribe(Subscribers.empty());")
                .interpretation("writeOnlyCommandCompletionSubject负责把变化调用 writeCommandCompletionsToShardedStreams 处理")
                .interpretation("1:writeCommandCompletionsToShardedStreams是自定义的一个函数，它主要作用其实就是 产生一个 HystrixCommandCompletionStream 传递给 writeOnlySubject，这样，通过 readOnlySubject 订阅的对象也就得到了变化的结果，然后一层层的下来算出健康值");
        //...
    }
























}
