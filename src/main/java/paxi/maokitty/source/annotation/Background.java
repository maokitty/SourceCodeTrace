package paxi.maokitty.source.annotation;

import java.lang.annotation.*;

/**
 * Created by maokitty on 19/5/3.
 * 介绍追踪的背景和目的
 */
@Retention(RetentionPolicy.SOURCE)
@Documented
@Target(ElementType.TYPE)
public @interface Background {
    String target();
    String conclusion();
    String sourceCodeProjectName();
    String projectVersion();
    String sourceCodeAddress();
}
