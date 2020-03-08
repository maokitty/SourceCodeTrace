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
    String sourceCodeInProjectDesc() default "项目中出现的代码均有删减，只提取相对核心的部分";
}
