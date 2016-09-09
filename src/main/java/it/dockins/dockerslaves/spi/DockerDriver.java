package it.dockins.dockerslaves.spi;

import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import it.dockins.dockerslaves.Container;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;

/**
 * Manage Docker resources creation and access so docker-slaves can run a build.
 * <p>
 * Implementation is responsible to adapt docker infrastructure APIs
 */
public interface DockerDriver extends Closeable {

    String createVolume(Launcher launcher, String driver, Collection<String> driverOpts) throws IOException, InterruptedException;

    boolean hasVolume(Launcher launcher, String name) throws IOException, InterruptedException;

    boolean hasContainer(Launcher launcher, String id) throws IOException, InterruptedException;

    Container launchRemotingContainer(Launcher launcher, String workdir, SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException;

    Container launchScmContainer(Launcher launcher, Container remotingContainer) throws IOException, InterruptedException;

    Container launchBuildContainer(Launcher launcher, String image, Container remotingContainer) throws IOException, InterruptedException;

    Proc execInContainer(Launcher launcher, String containerId, Launcher.ProcStarter starter) throws IOException, InterruptedException;

    int removeContainer(Launcher launcher, Container instance) throws IOException, InterruptedException;

    void launchSideContainer(Launcher launcher, Container instance, Container remotingContainer) throws IOException, InterruptedException;

    void pullImage(Launcher launcher, String image) throws IOException, InterruptedException;

    boolean checkImageExists(Launcher launcher, String image) throws IOException, InterruptedException;

    int buildDockerfile(Launcher launcher, String dockerfilePath, String tag, boolean pull) throws IOException, InterruptedException;

    /**
     * Return server version string, used actually to check connectivity with backend
     */
    String serverVersion(Launcher launcher) throws IOException, InterruptedException;
}
