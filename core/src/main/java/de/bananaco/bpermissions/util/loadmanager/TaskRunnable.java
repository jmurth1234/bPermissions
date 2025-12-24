package de.bananaco.bpermissions.util.loadmanager;

public interface TaskRunnable extends Runnable {

    enum TaskType {
        SAVE,
        LOAD,
        SERVER
    }

    TaskType getType();
}
