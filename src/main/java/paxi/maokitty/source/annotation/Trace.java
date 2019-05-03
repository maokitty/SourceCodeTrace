package paxi.maokitty.source.annotation;

import java.lang.annotation.*;

/**
 * Created by maokitty on 19/5/3.
 */
@Retention(RetentionPolicy.SOURCE)
@Documented
@Target(ElementType.METHOD)
public @interface Trace {
    int index();
    String originClassName();
    String introduction() default "";
    String function() default "省略就是自己定义的函数名";
    String more() default "";
}
