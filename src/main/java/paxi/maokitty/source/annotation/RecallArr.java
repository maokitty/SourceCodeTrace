package paxi.maokitty.source.annotation;

import java.lang.annotation.*;

/**
 * Created by liwangchun on 19/5/13.
 */
@Retention(RetentionPolicy.SOURCE)
@Documented
@Target({ElementType.TYPE,ElementType.METHOD})
public @interface RecallArr {
   Recall[] recalls();
}
