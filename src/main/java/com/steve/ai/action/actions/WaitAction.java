package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;

public class WaitAction extends BaseAction {
    private int ticksToWait;
    private int ticksRunning;

    public WaitAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        ticksToWait = Math.max(1, task.getIntParameter("ticks", 40));
        ticksRunning = 0;
        steve.getNavigation().stop();
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        if (ticksRunning >= ticksToWait) {
            result = ActionResult.success("Waited " + ticksRunning + " ticks");
        }
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Wait " + ticksToWait + " ticks";
    }
}
