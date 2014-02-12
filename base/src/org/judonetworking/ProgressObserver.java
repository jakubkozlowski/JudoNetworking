package org.judonetworking;

/**
 * Created with IntelliJ IDEA.
 * User: jbogacki
 * Date: 27.05.2013
 * Time: 13:07
 * To change this template use File | Settings | File Templates.
 */
public interface ProgressObserver {
    public void progressTick();

    public void progressTick(float progress);

    public void setMaxProgress(int max);

    public int getMaxProgress();

}