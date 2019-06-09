package paxi.maokitty.source.rxjava2;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/6/9.
 */
@Background(
        target = "了解rxjava2中的 Disposable disposable = Observable.just(1).subscribe(...)的流程",
        conclusion = "用户自己通过订阅（subscribe）传递自己的逻辑处理方法，rxJava内部会包装对应的Observer逻辑来完成对订阅事件的处理",
        sourceCodeProjectName = "Rxjava",
        sourceCodeAddress = "https://github.com/ReactiveX/RxJava",
        projectVersion = "2.2.8"

)
public class ObservableJustTrace {
    @Main
    @Trace(
            index = 0,
            originClassName = "io.reactivex.Observable",
            function = "public static <T> Observable<T> just(T item) "
    )
    public void just(){
        //...
        Code.SLICE.source("return RxJavaPlugins.onAssembly(new ObservableJust<T>(item));")
                .interpretation("使用具体的Observabl实现Just返回实例(这里不关注实现了 onObservableAssembly 的情况)");
    }
    @Trace(
            index = 1,
            originClassName = "io.reactivex.Observable",
            function = "public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError, Action onComplete, Consumer<? super Disposable> onSubscribe)"
    )
    public void subscribe(){
        //...
        Code.SLICE.source("LambdaObserver<T> ls = new LambdaObserver<T>(onNext, onError, onComplete, onSubscribe);")
                .interpretation("onNext即通过subscribe传过来的类，onNext为观察者观察到变化之后逻辑处理的部分（onError等其它部分不关注）");
        Code.SLICE.source("subscribe(ls);")
                .interpretation("执行订阅的逻辑，去掉部分非核心的逻辑之后，它真正的subscribe的实现是在 subscribeActual 。对于不同的 Observable有具体的不同实现，以下以 ObservableJust 为主");
        //...
    }
    @Trace(
            index = 2,
            originClassName = "io.reactivex.internal.operators.observable.ObservableJust",
            function = "protected void subscribeActual(Observer<? super T> observer)"
    )
    protected void subscribeActual(){
        Code.SLICE.source("ScalarDisposable<T> sd = new ScalarDisposable<T>(observer, value);")
                .interpretation("保存observer对象和Just传进来的值，这里的observer从上下文来说就是 LambdaObserver");
        //..
        Code.SLICE.source("sd.run();")
                .interpretation("执行LambdaObserver的 onNext方法，将上面保存的 value 作为参数传入，这里可以看到，如果订阅不发生，是不会存在这种推送逻辑");
    }

    @Trace(
            index = 3,
            originClassName = "io.reactivex.internal.observers.LambdaObserver",
            function = "public void onNext(T t) "
    )
    public void onNext(){
        Code.SLICE.source("if (!isDisposed())")
                .interpretation("订阅没有解除才执行,用户拿到的订阅的 Disposable 之后，可以执行 dispose() 来解除订阅关系");
        //...
        Code.SLICE.source("     onNext.accept(t);")
                .interpretation("执行订阅者自己的逻辑");
        //...
    }










}
