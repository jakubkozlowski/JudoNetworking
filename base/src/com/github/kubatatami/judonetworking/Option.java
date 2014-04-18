package com.github.kubatatami.judonetworking;

import java.io.Serializable;
import java.lang.annotation.Annotation;

/**
 * Created by Kuba on 17/04/14.
 */
public abstract class Option<T extends Annotation> implements Serializable{

    protected Option() {
    }

    protected Option(T annotation){

    }

}
