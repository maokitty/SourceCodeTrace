package paxi.maokitty.source.hystrix;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/5/30.
 */
@Background(
        target = "了解Hystrix的HealthCounts是怎么做的",
        conclusion = "",
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
                .interpretation("4:HystrixCommandMetrics.appendEventToBucket 是一个包含两个参数的函数，就是把命令的执行结果让如对应的计数数组中")
                ;
        //...
        Code.SLICE.source("healthStream.startCachingStreamValuesIfUnstarted();")
                .interpretation("缓存流产生的数据"); //todo 后面补充
        //...
    }
    @Trace(
            index = 1,
            originClassName = "com.netflix.hystrix.metric.consumer.HealthCountsStream",
            function = "private HealthCountsStream(final HystrixCommandKey commandKey, final int numBuckets, final int bucketSizeInMs Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion)",
            introduction = "构造函数"
    )
    public void  HealthCountsStreamConstructor(){
        //todo 后续看下是否有必要解释下 writeOnlySubject/readOnlyStream 或者看下  HystrixCommandCompletionStream 承担的角色
        Code.SLICE.source(" super(HystrixCommandCompletionStream.getInstance(commandKey), numBuckets, bucketSizeInMs, reduceCommandCompletion, healthCheckAccumulator);")
                .interpretation("1:HystrixCommandCompletionStream 命令执行完的流，在命令执行的线程里面，事件同步的发送,他的两个字段 writeOnlySubject，是一个经过SerializedSubject包装（保证线程安全）的PublishSubject ；readOnlyStream ,通过 writeOnlySubject.share产生 ")
                .interpretation("2:healthCheckAccumulator 就是承担着去计算指标,明确的算出成功了多少，失败了多少，并算出失败的比例")
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
    public void  BucketedRollingCounterStreamConstrutor(){
        Code.SLICE.source("super(stream, numBuckets, bucketSizeInMs, appendRawEventToBucket);")
                .interpretation("")
    }

    @Trace(
            index = 3,
            originClassName = "com.netflix.hystrix.metric.consumer.BucketedCounterStream",
            function = " protected BucketedCounterStream(final HystrixEventStream<Event> inputEventStream, final int numBuckets, final int bucketSizeInMs,\n" +
                        " final Func2<Bucket, Event, Bucket> appendRawEventToBucket)",
            introduction = "BucketedRollingCounterStream的父构造函数"
    )

    public void BucketedCounterStreamConstrutor(){
        //...
        Code.SLICE.source(" this.reduceBucketToSummary = new Func1<Observable<Event>, Observable<Bucket>>() {\n" +
                "            @Override\n" +
                "            public Observable<Bucket> call(Observable<Event> eventBucket) {\n" +
                "                return eventBucket.reduce(getEmptyBucketSummary(), appendRawEventToBucket);\n" +
                "            }\n" +
                "        };")
                .interpretation("");
    }









}
