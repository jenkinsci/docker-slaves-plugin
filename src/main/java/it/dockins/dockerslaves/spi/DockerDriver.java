package it.dockins.dockerslaves.spi;

import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import it.dockins.dockerslaves.Container;
import it.dockins.dockerslaves.DockerComputer;
import it.dockins.dockerslaves.spec.Hint;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Manage Docker resources creation and access so docker-slaves can run a build.
 * <p>
 * Implementation is responsible to adapt docker infrastructure APIs
 */
public abstract class DockerDriver implements Closeable {

    public abstract boolean hasVolume(TaskListener listener, String name) throws IOException, InterruptedException;

    public abstract String createVolume(TaskListener listener) throws IOException, InterruptedException;

    public abstract boolean hasContainer(TaskListener listener, String id) throws IOException, InterruptedException;

    public abstract Container launchRemotingContainer(TaskListener listener, String image, String workdir, DockerComputer computer) throws IOException, InterruptedException;

    public abstract Container launchBuildContainer(TaskListener listener, String image, Container remotingContainer, List<Hint> hints) throws IOException, InterruptedException;

    public abstract Container launchSideContainer(TaskListener listener, String image, Container remotingContainer, List<Hint> hints) throws IOException, InterruptedException;

    public abstract Proc execInContainer(TaskListener listener, String containerId, Launcher.ProcStarter starter) throws IOException, InterruptedException;

    public abstract void removeContainer(TaskListener listener, Container instance) throws IOException, InterruptedException;

    public abstract void pullImage(TaskListener listener, String image) throws IOException, InterruptedException;

    public abstract boolean checkImageExists(TaskListener listener, String image) throws IOException, InterruptedException;

    public abstract void buildDockerfile(TaskListener listener, String dockerfilePath, String tag, boolean pull) throws IOException, InterruptedException;

    /**
     * Return server version string, used actually to check connectivity with backend
     */
    public abstract String serverVersion(TaskListener listener) throws IOException, InterruptedException;
}
