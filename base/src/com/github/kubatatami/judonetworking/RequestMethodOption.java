package com.github.kubatatami.judonetworking;

import com.github.kubatatami.judonetworking.Option;
import com.github.kubatatami.judonetworking.RequestMethod;

/**
 * Created by Kuba on 09/04/14.
 */
public final class RequestMethodOption extends Option<RequestMethod> {

    private String name;
    private String[] paramNames=new String[0];
    private int timeout;
    private boolean async;
    private boolean highPriority;
    private boolean allowEmptyResult;


    public RequestMethodOption() {
    }

    public RequestMethodOption(RequestMethod annotation) {
        this.name=annotation.name();
        this.paramNames=annotation.paramNames();
        this.timeout=annotation.timeout();
        this.async=annotation.async();
        this.highPriority=annotation.highPriority();
        this.allowEmptyResult=annotation.allowEmptyResult();
    }

    public String getName() {
        return name;
    }

    public RequestMethodOption setName(String name) {
        this.name = name;
        return this;
    }

    public String[] getParamNames() {
        return paramNames;
    }

    public RequestMethodOption setParamNames(String[] paramNames) {
        this.paramNames = paramNames;
        return this;
    }

    public int getTimeout() {
        return timeout;
    }

    public RequestMethodOption setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public boolean isHighPriority() {
        return highPriority;
    }

    public RequestMethodOption setHighPriority(boolean highPriority) {
        this.highPriority = highPriority;
        return this;
    }

    public boolean isAllowEmptyResult() {
        return allowEmptyResult;
    }

    public RequestMethodOption setAllowEmptyResult(boolean allowEmptyResult) {
        this.allowEmptyResult = allowEmptyResult;
        return this;
    }
}
