package it.dockins.dockerslaves.spi;

import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import it.dockins.dockerslaves.Container;

import java.io.Closeable;
import java.io.IOException;

/**
 * Manage Docker resources creation and access so docker-slaves can run a build.
 * <p>
 * Implementation is responsible to adapt docker infrastructure APIs
 */
public interface DockerDriver extends Closeable {

    boolean hasVolume(TaskListener listener, String name) throws IOException, InterruptedException;

    String createVolume(TaskListener listener) throws IOException, InterruptedException;

    boolean hasContainer(TaskListener listener, String id) throws IOException, InterruptedException;

    Container launchRemotingContainer(TaskListener listener, String dockerImage, String workdir, SlaveComputer computer) throws IOException, InterruptedException;

    Container launchBuildContainer(TaskListener listener, String image, Container remotingContainer) throws IOException, InterruptedException;

    Proc execInContainer(TaskListener listener, String containerId, Launcher.ProcStarter starter) throws IOException, InterruptedException;

    int removeContainer(TaskListener listener, Container instance) throws IOException, InterruptedException;

    void launchSideContainer(TaskListener listener, Container instance, Container remotingContainer) throws IOException, InterruptedException;

    void pullImage(TaskListener listener, String image) throws IOException, InterruptedException;

    boolean checkImageExists(TaskListener listener, String image) throws IOException, InterruptedException;

    int buildDockerfile(TaskListener listener, String dockerfilePath, String tag, boolean pull) throws IOException, InterruptedException;

    /**
     * Return server version string, used actually to check connectivity with backend
     */
    String serverVersion(TaskListener listener) throws IOException, InterruptedException;
}
