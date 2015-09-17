package com.cloudbees.jenkins.plugins.containerslaves;

import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;

public abstract class ContainerProvisioner<T extends ContainerBuildContext>  {
    protected T context;

    public ContainerProvisioner(T context) {
        this.context = context;
    }

    public abstract void preparePod();

    public abstract void connectRemoting(final SlaveComputer computer, TaskListener listener);
}