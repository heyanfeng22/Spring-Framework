package com.gpSpringFreamWork.annotation;

import java.lang.annotation.*;

/**
 * @Autor : heyanfeng22
 * @Description :
 * @Date:Create:in 2019/3/27 15:04
 * @Modified By:
 */
@Target({ElementType.TYPE})   //接口、类、枚举、注解
@Retention(RetentionPolicy.RUNTIME)    // 注解会在class字节码文件中存在，在运行时可以通过反射获取到
@Documented
public @interface GPService
{
    String value() default "";
}
