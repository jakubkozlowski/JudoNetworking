package com.jsonrpclib;


import android.app.Fragment;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created with IntelliJ IDEA.
 * User: jbogacki
 * Date: 23.04.2013
 * Time: 11:40
 */
public class JsonFragmentCallback<T> extends JsonCallback<T> {

    private final Fragment fragment;

    public JsonFragmentCallback(Fragment fragment) {
        this.fragment = fragment;
    }

    public final void onFinish(T result) {
        if (fragment.getActivity() != null) {
            onSafeFinish(result);
        }
    }

    public final void onError(Exception e) {
        if (fragment.getActivity() != null) {
            onSafeError(e);
        }
    }


    public void onSafeFinish(T result)
    {

    }

    public void onSafeError(Exception e) {
        if (e != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            JsonLoggerImpl.log(sw.toString());
        } else {
            JsonLoggerImpl.log("Null exception");
        }
    }


}
