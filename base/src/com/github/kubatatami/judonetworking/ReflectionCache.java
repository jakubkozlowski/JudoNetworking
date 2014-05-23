package com.github.kubatatami.judonetworking;

import android.support.v4.util.LruCache;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Created by Kuba on 23/05/14.
 */
class ReflectionCache {

    protected final static LruCache<Class<?>, Annotation[]> interfaceAnnotationCache = new LruCache<Class<?>, Annotation[]> (100);
    protected final static LruCache<Method, Annotation[]> methodAnnotationCache = new LruCache<Method, Annotation[]>(100);
    protected final static LruCache<Method, Type[]> methodParamsTypeCache = new LruCache<Method, Type[]>(100);

    public static Annotation[] getAnnotations(Class<?> apiInterface){
        Annotation[] result = interfaceAnnotationCache.get(apiInterface);
        if(result==null){
            result=apiInterface.getAnnotations();
            interfaceAnnotationCache.put(apiInterface,result);
        }
        return result;
    }

    public static Annotation[] getAnnotations(Method method){
        Annotation[] result = methodAnnotationCache.get(method);
        if(result==null){
            result=method.getAnnotations();
            methodAnnotationCache.put(method,result);
        }
        return result;
    }


    public static <T> T getAnnotation(Class<?> apiInterface, Class<T> annotationClass) {
        Annotation[] annotations=getAnnotations(apiInterface);
        for(Annotation annotation : annotations){
            if(annotationClass.isInstance(annotation)){
                return (T) annotation;
            }
        }
        return null;
    }

    public static <T> T getAnnotation(Method method, Class<T> annotationClass) {
        Annotation[] annotations=getAnnotations(method);
        for(Annotation annotation : annotations){
            if(annotationClass.isInstance(annotation)){
                return (T) annotation;
            }
        }
        return null;
    }

    public static Type[] getGenericParameterTypes(Method method){
        Type[] result = methodParamsTypeCache.get(method);
        if(result==null){
            result=method.getGenericParameterTypes();
            methodParamsTypeCache.put(method,result);
        }
        return result;
    }

}
