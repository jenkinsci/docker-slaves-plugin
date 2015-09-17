package com.cloudbees.jenkins.plugins.containerslaves;

import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.slaves.CommandLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;

public class DockerProvisioner<T extends DockerBuildContext>  {

    private final DockerBuildContext context;

    private final TaskListener listener;

    private final DockerDriver driver;

    private final Launcher localLauncher;

    public DockerProvisioner(DockerBuildContext context, DockerDriver driver, TaskListener listener) {
        this.context = context;
        this.listener = listener;
        this.driver = driver;
        localLauncher = new Launcher.LocalLauncher(listener);
    }

    public void preparePod() {
        try {
            String remotingContainer = driver.createRemotingContainer(localLauncher, context.getRemotingContainerImageName());
            context.setRemotingContainerId(remotingContainer);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void connectRemoting(final SlaveComputer computer, TaskListener listener) {
        CommandLauncher launcher = new CommandLauncher("docker start -ia " + context.getRemotingContainerId());
        launcher.launch(computer, listener);
    }

}