package com.avit.downloadmanager.executor;

import androidx.core.util.Pair;

import com.avit.downloadmanager.task.ITask;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public abstract class AbsExecutor implements Callable<Boolean> {

    private ConcurrentHashMap<String, Pair<ITask, FutureTask<Boolean>>> futureTasks;

    public AbsExecutor() {
        futureTasks = new ConcurrentHashMap<>();
    }

    protected Pair<ITask, FutureTask<Boolean>> putIfAbsent(String key, Pair<ITask, FutureTask<Boolean>> value){
        return futureTasks.putIfAbsent(key, value);
    }

    public abstract AbsExecutor submit(ITask task);

    public boolean isFinish(ITask task){

        String key = task.getDownloadItem().getKey();

        Pair<ITask, FutureTask<Boolean>> pair = futureTasks.get(key);
        FutureTask<Boolean> futureTask = pair.second;
        if (futureTask == null || futureTask.isDone() || futureTask.isCancelled()){
            futureTasks.remove(key);
            return true;
        }

        return false;
    }

    public ITask.State getTaskState(ITask task){
        String key = task.getDownloadItem().getKey();

        Pair<ITask, FutureTask<Boolean>> pair = futureTasks.get(key);
        FutureTask<Boolean> futureTask = pair.second;
        if (futureTask == null || futureTask.isDone() || futureTask.isCancelled()){
            futureTasks.remove(key);
            return ITask.State.NONE;
        }

        ITask t = pair.first;
        if (t != null){
            return t.getState();
        }

        return task.getState();
    }

    public final boolean isExist(ITask task){
        return futureTasks.containsKey(task.getDownloadItem().getKey());
    }

    @Override
    public Boolean call() throws Exception {

        Set<Map.Entry<String, Pair<ITask, FutureTask<Boolean>>>> entrySet = futureTasks.entrySet();
        Iterator<Map.Entry<String, Pair<ITask, FutureTask<Boolean>>>> iterator = entrySet.iterator();

        for (; iterator.hasNext(); ) {
            Map.Entry<String, Pair<ITask, FutureTask<Boolean>>> entry = iterator.next();
            Pair<ITask, FutureTask<Boolean>> pair = entry.getValue();
            FutureTask<Boolean> task = pair.second;
            if (task == null || task.isDone() || task.isCancelled()){
                iterator.remove();
            }
        }

        return true;
    }

    public void release(){

        Enumeration<String> enumeration = futureTasks.keys();

        while (enumeration.hasMoreElements()){
            String key = enumeration.nextElement();
            ITask task = futureTasks.get(key).first;

            if (task != null) {
                task.release();
            }

            Future<Boolean> future = futureTasks.get(key).second;
            if (future != null){
                future.cancel(true);
            }
        }

        futureTasks.clear();
    }
}
