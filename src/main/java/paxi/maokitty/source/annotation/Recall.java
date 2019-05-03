package paxi.maokitty.source.annotation;

import java.lang.annotation.*;

/**
 * Created by maokitty on 19/5/3.
 * 做些上下文的提醒
 */
@Retention(RetentionPolicy.SOURCE)
@Documented
@Target({ElementType.TYPE,ElementType.METHOD})
public @interface Recall {
    int traceIndex();
    String tip();
}
