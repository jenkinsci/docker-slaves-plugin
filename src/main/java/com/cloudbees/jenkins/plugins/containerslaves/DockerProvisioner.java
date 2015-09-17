package com.cloudbees.jenkins.plugins.containerslaves;

import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.slaves.CommandLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;

/**
 * Provision {@link DockerSlave} to provide queued task an executor.
 */
public class DockerProvisioner  {

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
            // if remoting container already exists, we just use it
            if (context.getRemotingContainerId() != null) {
                if (driver.hasContainer(localLauncher, context.getRemotingContainerId())) {
                    return;
                }
            }
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