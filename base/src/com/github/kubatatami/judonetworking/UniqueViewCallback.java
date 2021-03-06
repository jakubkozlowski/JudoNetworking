package com.github.kubatatami.judonetworking;

import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Kuba on 23/05/14.
 */
public class  UniqueViewCallback<Z,T> extends Callback<T>{

    protected static final Map<View, UniqueViewCallback> viewCache = new HashMap<View, UniqueViewCallback>();
    protected final View view;
    protected AsyncResult asyncResult;
    protected Z object;

    public UniqueViewCallback(View view) {
        this.view = view;
    }

    public void setObject(Z object) {
        this.object = object;
        cancelRequest(view);
        viewCache.put(view,this);
    }

    @Override
    public void onStart(CacheInfo cacheInfo, AsyncResult asyncResult) {
        super.onStart(cacheInfo, asyncResult);
        this.asyncResult=asyncResult;
        if(viewCache.get(view)!=this) {
            asyncResult.cancel();
        }
    }

    @Override
    public void onFinish() {
        super.onFinish();
        if(viewCache.containsKey(view) && viewCache.get(view)==this){
            viewCache.remove(view);
        }
    }


    public static void cancelRequest(View view){
        if(viewCache.containsKey(view)) {
            if(viewCache.get(view).asyncResult!=null) {
                viewCache.get(view).asyncResult.cancel();
            }
            viewCache.remove(view);
        }
    }

}
