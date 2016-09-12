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

    /**
     * Launch a container to host jenkins remoting agent and establish a channel as a Jenkins slave.
     */
    Container launchRemotingContainer(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException;

    /**
     * Launch a container whith adequate tools to run the SCM checkout build phase.
     */
    Container launchScmContainer(TaskListener listener) throws IOException, InterruptedException;

    /**
     * Launch build environment as defined by (@link Job}'s {@link it.dockins.dockerslaves.spec.ContainerSetDefinition}.
     */
    Container launchBuildContainers(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException;

    /**
     * Run specified process inside the main build container
     */
    Proc launchBuildProcess(Launcher.ProcStarter procStarter, TaskListener listener) throws IOException, InterruptedException;

    /**
     * Cleanup all allocated resources
     */
    void clean(TaskListener listener) throws IOException, InterruptedException;
}
