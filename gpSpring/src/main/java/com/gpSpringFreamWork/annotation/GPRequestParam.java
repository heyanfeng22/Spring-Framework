package com.gpSpringFreamWork.annotation;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})   //接口、类、枚举、注解
@Retention(RetentionPolicy.RUNTIME)    // 注解会在class字节码文件中存在，在运行时可以通过反射获取到
@Documented    //说明该注解将被包含在javadoc中
public @interface GPRequestParam
{
    String value() default "";
}


