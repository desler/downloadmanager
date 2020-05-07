package com.avit.downloadmanager.task.retry;

import android.util.Log;

import com.avit.downloadmanager.data.DownloadItem;

public final class RetryConfig implements RetryListener {

    public static RetryConfig create() {
        return new RetryConfig();
    }

    private RetryListener retryListener;
    /**
     * default retry task
     */
    private boolean isRetry = true;
    /**
     * default retry 3 times
     */
    private int retryCount;
    /**
     * delayed timer, ms
     */
    private long baseDelayed;
    /**
     * 5 10 15 20 25 30
     */
    private long step;

    private int repeatStepTimes;

    private boolean isStableDelayed;

    private RetryConfig() {
        this.retryListener = this;

        this.isRetry = true;
        this.retryCount = 3;

        this.baseDelayed = 0;
        this.step = 5 * 1000;
        this.repeatStepTimes = 5;

        this.isStableDelayed = false;
    }

    public RetryConfig withRetryListener(RetryListener retryListener) {
        this.retryListener = retryListener;
        return this;
    }

    /**
     * @param step ms
     * @return
     */
    public RetryConfig withRetryStep(long step) {
        this.step = step;
        return this;
    }

    /**
     * @param times
     * @return
     */
    public RetryConfig withRetryStepTimes(int times) {
        this.repeatStepTimes = times;
        return this;
    }

    /**
     * @param delayed ms
     * @return
     */
    public RetryConfig withRetryDelayed(long delayed){
        this.baseDelayed = delayed;
        return this;
    }

    public RetryConfig retry() {
        this.isRetry = true;
        this.retryCount = 3;

        return this;
    }

    public RetryConfig stableRetry() {
        retry();
        this.isStableDelayed = true;

        return this;
    }

    public RetryConfig retry(int count) {
        this.isRetry = true;
        this.retryCount = count;

        return this;
    }

    public RetryConfig stableRetry(int count) {
        retry(count);
        this.isStableDelayed = true;

        return this;
    }

    public RetryConfig notRetry() {
        this.isRetry = false;

        return this;
    }

    @Override
    public void onRetry(DownloadItem item, int retryCount, int reason, String message) {
        Log.w("RetryConfig", "onRetry: dummy ");
    }


    /**
     * ------------- getxxx-----------------
     */

    public RetryListener getRetryListener() {
        return retryListener;
    }

    public boolean isRetry() {
        return isRetry;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getBaseDelayed() {
        return baseDelayed;
    }

    public long getStep() {
        return step;
    }

    public int getRepeatStepTimes() {
        return repeatStepTimes;
    }

    public boolean isStableDelayed() {
        return isStableDelayed;
    }
}
