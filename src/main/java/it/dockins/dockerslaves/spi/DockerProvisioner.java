package it.dockins.dockerslaves.spi;

import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import it.dockins.dockerslaves.Container;
import it.dockins.dockerslaves.ContainersContext;

import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface DockerProvisioner {

    ContainersContext getContext();

    Container launchRemotingContainer(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException;

    Container launchBuildContainer(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException;

    Container launchScmContainer(TaskListener listener) throws IOException, InterruptedException;

    Proc launchBuildProcess(Launcher.ProcStarter procStarter, TaskListener listener) throws IOException, InterruptedException;

    void clean(TaskListener listener) throws IOException, InterruptedException;
}
