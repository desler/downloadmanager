package com.avit.downloadmanager.data;

import android.util.Log;

import org.litepal.FluentQuery;
import org.litepal.LitePal;

import java.util.List;

public final class BreakPointHelper {
    private final static String TAG = "BreakPointHelper";

    public List<DLTempConfig> findByKey(String key){
        FluentQuery query = LitePal.where("key=?", key);
        return query.find(DLTempConfig.class);
    }

    public boolean save(DLTempConfig config){
        return config.saveOrUpdate("key=? and seq=?", config.key, String.valueOf(config.seq));
    }

    public int delete(DLTempConfig config){
        if (!config.isSaved()){
            Log.w(TAG, "delete: nothing -> " + config);
        }
        return config.delete();
    }

    public int deleteByKey(String key){
        return LitePal.deleteAll(DLTempConfig.class, "key=?", key);
    }
}
