package com.cloudbees.jenkins.plugins.containerslaves;

import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.lang.Override;import java.lang.String;import java.util.List;
import java.util.logging.Logger;

/**
 * Process launcher which uses docker exec instead of execve
 */
public class DockerLauncher extends Launcher.DecoratedLauncher {
    private static final Logger LOGGER = Logger.getLogger(DockerLauncher.class.getName());

    private final DockerProvisioner provisioner;

    private final Launcher localLauncher;

    public DockerLauncher(TaskListener listener, VirtualChannel channel, boolean isUnix, DockerProvisioner provisioner)  {
        super(new Launcher.RemoteLauncher(listener, channel, isUnix));
        this.provisioner = provisioner;
        this.localLauncher = new Launcher.LocalLauncher(listener);
    }

    @Override
    public Proc launch(ProcStarter starter) throws IOException {
        try {
            String containerId = provisioner.prepareRunOnBuildContainer(starter);
            if (!starter.quiet()) {
                listener.getLogger().append("In container "+provisioner.getContext().getBuildContainerName()+" (" + containerId.substring(0,9) + ")\n");
                maskedPrintCommandLine(starter.cmds(), starter.masks(), starter.pwd());
            }

            return provisioner.runOnBuildContainer(starter, containerId);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
