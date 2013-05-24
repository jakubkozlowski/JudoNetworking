package com.jsonrpclib;

import java.net.HttpURLConnection;

/**
 * Created with IntelliJ IDEA.
 * User: jbogacki
 * Date: 24.03.2013
 * Time: 21:53
 *
 */
interface JsonConnection {


    public HttpURLConnection get(String url, String request, int timeout,JsonTimeStat timeStat) throws Exception;
    public HttpURLConnection post(String url, Object request, int timeout,JsonTimeStat timeStat) throws Exception;

    public void setMaxConnections(int max);

    public void setReconnections(int reconnections);
    public void setConnectTimeout(int connectTimeout);
    public void setMethodTimeout(int methodTimeout);
    public int getMethodTimeout();

}
