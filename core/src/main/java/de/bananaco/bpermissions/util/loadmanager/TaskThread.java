package de.bananaco.bpermissions.util.loadmanager;

public interface TaskThread {

    /**
     * If there are any currently scheduled tasks, return true
     *
     * @return tasks.size() > 0
     */
    boolean hasTasks();

    /**
     * If the tasks are currently running
     *
     * @return running
     */
    boolean isRunning();

    /**
     * Used to prevent any more tasks from running
     *
     * @param running
     */
    void setRunning(boolean running);

    /**
     * Schedule a task to be run
     *
     * @param r
     */
    void schedule(TaskRunnable r);
}
