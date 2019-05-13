package paxi.maokitty.source.annotation;

import java.lang.annotation.*;

/**
 * Created by maokitty on 19/5/3.
 * 表明这部分是问题的关键
 */
@Retention(RetentionPolicy.SOURCE)
@Documented
@Target({ElementType.METHOD,ElementType.TYPE})
public @interface KeyPoint {
    String desc() default "";
}
