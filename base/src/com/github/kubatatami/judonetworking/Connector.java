package com.github.kubatatami.judonetworking;

import com.github.kubatatami.judonetworking.exceptions.ConnectionException;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Created with IntelliJ IDEA.
 * User: jbogacki
 * Date: 24.03.2013
 * Time: 21:53
 */
public abstract class Connector {

    public abstract Connection send(ProtocolController protocolController, ProtocolController.RequestInfo requestInfo,
                                    int timeout, TimeStat timeStat, int debugFlags, Method method, CacheInfo cacheInfo) throws ConnectionException;

    public abstract void setMaxConnections(int max);

    public abstract void setReconnections(int reconnections);

    public abstract void setConnectTimeout(int connectTimeout);

    public abstract void setMethodTimeout(int methodTimeout);

    public abstract int getMethodTimeout();


    public interface Connection {
        public InputStream getStream() throws Exception;

        public int getContentLength();

        public String getHash();

        public Long getDate();

        public void close();

        public Map<String, List<String>> getHeaders();

        public boolean isNewestAvailable() throws Exception;
    }

    public static class CacheInfo {
        public String hash;
        public Long time;

        public CacheInfo(String hash, Long time) {
            this.hash = hash;
            this.time = time;
        }
    }

    protected void longLog(String tag, String message) {
        LoggerImpl.longLog(tag, message);
    }

    protected static String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

}
