package com.github.kubatatami.judonetworking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created with IntelliJ IDEA.
 * User: jbogacki
 * Date: 24.09.2013
 * Time: 19:45
 * To change this template use File | Settings | File Templates.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Delay {

    public boolean enabled() default true;

    public int value() default 0;

}
