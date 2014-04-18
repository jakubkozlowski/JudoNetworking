package com.github.kubatatami.judonetworking;

import com.github.kubatatami.judonetworking.exceptions.JudoException;

import java.lang.reflect.Type;

/**
 * Created by Kuba on 08/04/14.
 */
public interface EndpointClassic extends EndpointBase {

    public <T> T sendRequest(String url, RequestMethodOption requestMethodOption,
                             Type returnType,
                             Object... args) throws JudoException;

    public <T> AsyncResult sendAsyncRequest(String url,
                                            RequestMethodOption requestMethodOption,
                                            CallbackInterface<T> callback, Object... args);

}
