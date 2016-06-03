package it.dockins.dockerslaves.drivers;

import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import it.dockins.dockerslaves.ContainerInstance;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;

public interface DockerDriver extends Closeable {
    String createVolume(Launcher launcher, String driver, Collection<String> driverOpts) throws IOException, InterruptedException;

    boolean hasVolume(Launcher launcher, String name) throws IOException, InterruptedException;

    boolean hasContainer(Launcher launcher, String id) throws IOException, InterruptedException;

    ContainerInstance createRemotingContainer(Launcher launcher, String image, String workdir) throws IOException, InterruptedException;

    void launchRemotingContainer(final SlaveComputer computer, TaskListener listener, ContainerInstance remotingContainer);

    ContainerInstance createAndLaunchBuildContainer(Launcher launcher, String image, ContainerInstance remotingContainer) throws IOException, InterruptedException;

    Proc execInContainer(Launcher launcher, String containerId, Launcher.ProcStarter starter) throws IOException, InterruptedException;

    int removeContainer(Launcher launcher, ContainerInstance instance) throws IOException, InterruptedException;

    void launchSideContainer(Launcher launcher, ContainerInstance instance, ContainerInstance remotingContainer) throws IOException, InterruptedException;

    void pullImage(Launcher launcher, String image) throws IOException, InterruptedException;

    boolean checkImageExists(Launcher launcher, String image) throws IOException, InterruptedException;

    int buildDockerfile(Launcher launcher, String dockerfilePath, String tag, boolean pull)  throws IOException, InterruptedException;
}
