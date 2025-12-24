package de.bananaco.bpermissions.util.loadmanager;

import java.util.ArrayList;
import java.util.List;

import de.bananaco.bpermissions.util.loadmanager.TaskRunnable.TaskType;
import de.bananaco.bpermissions.util.Debugger;

public class MainThread extends Thread implements TaskThread {

    private static MainThread thread = new MainThread();

    public static MainThread getInstance() {
        return thread;
    }
    // a list of tasks
    private List<Runnable> load = new ArrayList<>();
    private List<Runnable> save = new ArrayList<>();
    private List<Runnable> server = new ArrayList<>();
    private boolean running = true;
    private boolean started = false;

    @Override
    public void run() {
        while (running) {
            check();
        }
    }

    /**
     * Internal method, check scheduler
     */
    private synchronized void check() {
        try {
            if (hasTasks()) {
                TaskRunnable run = null;
                List tasks = null;
                if (getTasks(TaskRunnable.TaskType.LOAD).size() > 0) {
                    tasks = getTasks(TaskRunnable.TaskType.LOAD);
                    setThreadName("LOAD");
                } else if (getTasks(TaskRunnable.TaskType.SAVE).size() > 0) {
                    tasks = getTasks(TaskRunnable.TaskType.SAVE);
                    setThreadName("SAVE");
                } else if (getTasks(TaskRunnable.TaskType.SERVER).size() > 0) {
                    tasks = getTasks(TaskRunnable.TaskType.SERVER);
                    setThreadName("SERVER");
                }

                if (tasks != null) {
                    run = (TaskRunnable) tasks.get(0);
                    tasks.remove(0);
                    TaskRunnable r = run;

                    r.run();
                }
            } else {
                sleep(10L);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Internal method, concurrent modification exception prevention
     *
     * @return List<Runnable>
     */
    private List<Runnable> getTasks(TaskType type) {
        if (type == TaskType.LOAD) {
            return load;
        }
        if (type == TaskType.SAVE) {
            return save;
        }
        if (type == TaskType.SERVER) {
            return server;
        }
        return new ArrayList<>();
    }

    // from the interface
    public boolean hasTasks() {
        return load.size() + save.size() + server.size() > 0;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(final boolean running) {
        clearTasks();
        if (!running) {
            thread.running = false;
            thread = null;
        }
    }

    public void clearTasks() {
        load.clear();
        save.clear();
        server.clear();
    }

    public boolean getStarted() {
        return started;
    }

    public void setStarted(final boolean started) {
        TaskRunnable r = new TaskRunnable() {
            public void run() {
                thread.started = started;
                Debugger.log("Set started: " + started);
            }

            public TaskType getType() {
                return TaskType.SERVER;
            }
        };
        schedule(r);
    }

    public void schedule(TaskRunnable r) {
        if (tasksCount() > 10 && (r.getType() == TaskType.LOAD || r.getType() == TaskType.SAVE)) {
            return;
        }

        getTasks(r.getType()).add(r);
    }

    public int tasksCount() {
        return load.size() + save.size();
    }

    private void setThreadName(String s) {
        setName("MainThread/" + s);
    }
}
