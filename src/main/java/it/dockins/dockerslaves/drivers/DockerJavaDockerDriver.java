/*
 * The MIT License
 *  
 *  Copyright (c) 2015, CloudBees, Inc.
 *  
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *  
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package it.dockins.dockerslaves.drivers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.CreateVolumeResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.VolumesFrom;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.netty.DockerCmdExecFactoryImpl;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.org.apache.tools.tar.TarOutputStream;
import hudson.slaves.CommandLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.ArgumentListBuilder;
import it.dockins.dockerslaves.ContainerInstance;
import it.dockins.dockerslaves.DockerSlave;
import it.dockins.dockerslaves.ProvisionQueueListener;
import it.dockins.dockerslaves.spi.DockerDriver;
import it.dockins.dockerslaves.spi.DockerHostConfig;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.dockerjava.core.DockerClientConfig.*;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author <a href="mailto:yoann.dubreuil@gmail.com">Yoann Dubreuil</a>
 */
public class DockerJavaDockerDriver implements DockerDriver {

    private final boolean verbose;

    final DockerHostConfig dockerHost;

    private final DockerClient client;

    public DockerJavaDockerDriver(DockerHostConfig dockerHost) throws IOException, InterruptedException {
        this.dockerHost = dockerHost;
        verbose = true;
        final EnvVars env = dockerHost.getEnvironment();
        DockerClientConfig config = new DockerClientConfigBuilder()
                .withDockerHost(env.get(DOCKER_HOST, "unix:///var/run/docker.sock"))
                .withDockerTlsVerify(env.get(DOCKER_TLS_VERIFY, "false"))
                .withDockerCertPath(env.get(DOCKER_CERT_PATH, ""))
                .withApiVersion("1.22")
                .withRegistryUsername(env.get(REGISTRY_USERNAME, ""))
                .withRegistryPassword(env.get(REGISTRY_PASSWORD, ""))
                .withRegistryEmail(env.get(REGISTRY_EMAIL, ""))
                .withRegistryUrl(env.get(REGISTRY_URL, ""))
                .build();
        client = DockerClientBuilder.getInstance(config).withDockerCmdExecFactory(new DockerCmdExecFactoryImpl()).build();
    }

    @Override
    public void close() throws IOException {
        dockerHost.close();
        client.close();
    }

    @Override
    public String createVolume(Launcher launcher, String driver, Collection<String> driverOpts) throws IOException, InterruptedException {
        CreateVolumeCmd cmd = client.createVolumeCmd();

        if (driver != null) {
            cmd.withDriver(driver);
            if (driverOpts != null) {
                //cmd.withDriverOpts(driverOpts);
            }
        }
        CreateVolumeResponse response = cmd.exec();
        return response.getName();
    }

    @Override
    public boolean hasVolume(Launcher launcher, String name) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(name)) {
            return false;
        }

        try {
            client.inspectVolumeCmd(name).exec();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean hasContainer(Launcher launcher, String id) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(id)) {
            return false;
        }

        InspectContainerResponse response = client.inspectContainerCmd(id).exec();
        if (response.getState().getStatus().equals("")) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public ContainerInstance createRemotingContainer(Launcher launcher, String image, String workdir) throws IOException, InterruptedException {

        CreateContainerCmd cmd = client.createContainerCmd(image);

        // --interactive
        cmd.withStdInOnce(true).withStdinOpen(true).withAttachStdin(true).withAttachStdout(true).withAttachStderr(true);

        cmd.withLogConfig(new LogConfig().setType(LogConfig.LoggingType.NONE));
        cmd.withEnv("TMPDIR="+ DockerSlave.SLAVE_ROOT+".tmp");
        cmd.withUser("10000:10000");
        //  cmd.withVolumes(new Volume(workdir+":"+ DockerSlave.SLAVE_ROOT));
        cmd.withWorkingDir(DockerSlave.SLAVE_ROOT);
        cmd.withCmd("java", "-Djava.io.tmpdir="+ DockerSlave.SLAVE_ROOT+".tmp", "-jar", DockerSlave.SLAVE_ROOT+"slave.jar");

        CreateContainerResponse response = cmd.exec();
        String containerId = response.getId();

        if (containerId == null) {
            throw new IOException("Failed to create docker image");
        }

        putFileContent(launcher, containerId, DockerSlave.SLAVE_ROOT, "slave.jar", new Slave.JnlpJar("slave.jar").readFully());
        return new ContainerInstance(image, containerId);
    }

    @Override
    public void launchRemotingContainer(final SlaveComputer computer, TaskListener listener, ContainerInstance remotingContainer) {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("start")
                .add("--interactive", "--attach", remotingContainer.getId());
        prependArgs(args);
        CommandLauncher launcher = new CommandLauncher(args.toString(), dockerHost.getEnvironment());
        launcher.launch(computer, listener);
    }

    @Override
    public ContainerInstance createAndLaunchBuildContainer(Launcher launcher, String image, ContainerInstance remotingContainer) throws IOException, InterruptedException {
        ContainerInstance buildContainer = new ContainerInstance(image);
        CreateContainerCmd cmd = client.createContainerCmd(image);
        cmd.withEnv("TMPDIR="+ DockerSlave.SLAVE_ROOT+".tmp");
        cmd.withUser("10000:10000");
        cmd.withWorkingDir(DockerSlave.SLAVE_ROOT);

        cmd.withVolumesFrom(new VolumesFrom(remotingContainer.getId()));
        cmd.withNetworkMode("container:" + remotingContainer.getId());
        // not implemented in docker-java
        // add("--ipc=container:" + remotingContainer.getId())

        cmd.withCmd("/trampoline", "wait");

        CreateContainerResponse response = cmd.exec();
        String containerId = response.getId();

        if (containerId == null) {
            throw new IOException("Failed to create docker image");
        }
        buildContainer.setId(containerId);

        injectJenkinsUnixGroup(launcher, containerId);
        injectJenkinsUnixUser(launcher, containerId);
        injectTrampoline(launcher, containerId);

        client.startContainerCmd(containerId).exec();
        if (!client.inspectContainerCmd(containerId).exec().getState().getRunning()) {
            throw new IOException("Failed to run docker image");
        }

        return buildContainer;
    }

    protected void injectJenkinsUnixGroup(Launcher launcher, String containerId) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        getFileContent(launcher, containerId, "/etc/group", out);
        out.write("jenkins:x:10000:\n".getBytes());
        putFileContent(launcher, containerId, "/etc", "group", out.toByteArray());
    }

    protected void injectJenkinsUnixUser(Launcher launcher, String containerId) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        getFileContent(launcher, containerId, "/etc/passwd", out);
        out.write("jenkins:x:10000:10000::/home/jenkins:/bin/false\n".getBytes());
        putFileContent(launcher, containerId, "/etc", "passwd", out.toByteArray());
    }

    protected void injectTrampoline(Launcher launcher, String containerId) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOUtils.copy(getClass().getResourceAsStream("/it/dockins/dockerslaves/trampoline"),out);
        putFileContent(launcher, containerId, "/", "trampoline", out.toByteArray(), 555);
    }

    protected void getFileContent(Launcher launcher, String containerId, String filename, OutputStream outputStream) throws IOException, InterruptedException {
        try (InputStream stream = client.copyArchiveFromContainerCmd(containerId, filename).exec()) {
            TarInputStream tar = new TarInputStream(stream);
            tar.getNextEntry();
            tar.copyEntryContents(outputStream);
            tar.close();
        }
    }

    protected int putFileContent(Launcher launcher, String containerId, String path, String filename, byte[] content) throws IOException, InterruptedException {
        return putFileContent(launcher, containerId, path, filename, content, null);
    }

    protected int putFileContent(Launcher launcher, String containerId, String path, String filename, byte[] content, Integer mode) throws IOException, InterruptedException {
        TarEntry entry = new TarEntry(filename);
        entry.setUserId(0);
        entry.setGroupId(0);
        entry.setSize(content.length);
        if (mode != null) {
            entry.setMode(mode);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TarOutputStream tar = new TarOutputStream(out);
        tar.putNextEntry(entry);
        tar.write(content);
        tar.closeEntry();
        tar.close();
        ByteArrayInputStream is = new ByteArrayInputStream(out.toByteArray());

        client.copyArchiveToContainerCmd(containerId).withTarInputStream(is).withRemotePath(path).exec();
        return 0;
    }

    @Override
    public Proc execInContainer(Launcher launcher, String containerId, Launcher.ProcStarter starter) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("exec", containerId);

        if (starter.pwd() != null) {
            args.add("/trampoline", "cdexec", starter.pwd().getRemote());
        }
        args.add("env").add(starter.envs());

        List<String> originalCmds = starter.cmds();
        boolean[] originalMask = starter.masks();
        for (int i = 0; i < originalCmds.size(); i++) {
            boolean masked = originalMask == null ? false : i < originalMask.length ? originalMask[i] : false;
            args.add(originalCmds.get(i), masked);
        }

        Launcher.ProcStarter procStarter = launchDockerCLI(launcher, args);

        if (starter.stdout() != null) {
            procStarter.stdout(starter.stdout());
        }

        return procStarter.start();
    }

    @Override
    public int removeContainer(Launcher launcher, ContainerInstance instance) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("rm", "-f", instance.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        return status;
    }

    private static final Logger LOGGER = Logger.getLogger(ProvisionQueueListener.class.getName());

    @Override
    public void launchSideContainer(Launcher launcher, ContainerInstance instance, ContainerInstance remotingContainer) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("create")
                .add("--volumes-from", remotingContainer.getId())
                .add("--net=container:" + remotingContainer.getId())
                .add("--ipc=container:" + remotingContainer.getId())
                .add(instance.getImageName());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        final String containerId = out.toString("UTF-8").trim();
        instance.setId(containerId);

        if (status != 0) {
            throw new IOException("Failed to run docker image");
        }

        launchDockerCLI(launcher, new ArgumentListBuilder()
                .add("start", containerId)).start();
    }

    @Override
    public void pullImage(Launcher launcher, String image) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("pull")
                .add(image);

        int status =  launchDockerCLI(launcher, args)
                .stdout(launcher.getListener().getLogger()).join();

        if (status != 0) {
            throw new IOException("Failed to pull image " + image);
        }
    }

    @Override
    public boolean checkImageExists(Launcher launcher, String image) throws IOException, InterruptedException {
        return client.inspectImageCmd(image).exec().getId() != null;
    }

    @Override
    public int buildDockerfile(Launcher launcher, String dockerfilePath, String tag, boolean pull)  throws IOException, InterruptedException {
        String pullOption = "--pull=";
        if (pull) {
            pullOption += "true";
        } else {
            pullOption += "false";
        }
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("build")
                .add(pullOption)
                .add("-t", tag)
                .add(dockerfilePath);

        return launchDockerCLI(launcher, args)
                .stdout(launcher.getListener().getLogger()).join();
    }

    public void prependArgs(ArgumentListBuilder args){
        final DockerServerEndpoint endpoint = dockerHost.getEndpoint();
        if (endpoint.getUri() != null) {
            args.prepend("-H", endpoint.getUri());
        } else {
            LOGGER.log(Level.FINE, "no specified docker host");
        }

        args.prepend("docker");
    }

    private Launcher.ProcStarter launchDockerCLI(Launcher launcher, ArgumentListBuilder args) {
        prependArgs(args);

        return launcher.launch()
                .envs(dockerHost.getEnvironment())
                .cmds(args)
                .quiet(!verbose);
    }
}
