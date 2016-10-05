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

import hudson.Launcher;
import hudson.Proc;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.org.apache.tools.tar.TarOutputStream;
import hudson.slaves.CommandLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.ArgumentListBuilder;
import hudson.util.VersionNumber;
import it.dockins.dockerslaves.Container;
import it.dockins.dockerslaves.DockerSlave;
import it.dockins.dockerslaves.ProvisionQueueListener;
import it.dockins.dockerslaves.hints.MemoryHint;
import it.dockins.dockerslaves.hints.VolumeHint;
import it.dockins.dockerslaves.spec.Hint;
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static it.dockins.dockerslaves.DockerSlave.SLAVE_ROOT;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author <a href="mailto:yoann.dubreuil@gmail.com">Yoann Dubreuil</a>
 */
public class CliDockerDriver extends DockerDriver {

    private final static boolean verbose = Boolean.getBoolean(DockerDriver.class.getName()+".verbose");;

    private final DockerHostConfig dockerHost;

    private final VersionNumber version;

    public CliDockerDriver(DockerHostConfig dockerHost) throws IOException, InterruptedException {
        this.dockerHost = dockerHost;
        // Also acts as sanity check to ensure host and credentials are well set
        version = new VersionNumber(serverVersion(TaskListener.NULL));
    }

    @Override
    public void close() throws IOException {
        dockerHost.close();
    }

    @Override
    public String createVolume(TaskListener listener) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("volume", "create");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        final String volume = out.toString(UTF_8).trim();

        if (status != 0) {
            throw new IOException("Failed to create docker volume");
        }

        return volume;
    }

    @Override
    public boolean hasVolume(TaskListener listener, String name) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(name)) {
            return false;
        }

        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("volume", "inspect", "-f", "'{{.Name}}'", name);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        if (status != 0) {
            return false;
        } else {
            return true;
        }
    }


    @Override
    public boolean hasContainer(TaskListener listener, String id) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(id)) {
            return false;
        }

        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("inspect", "-f", "'{{.Id}}'", id);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        if (status != 0) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public Container launchRemotingContainer(TaskListener listener, String image, String volume, SlaveComputer computer) throws IOException, InterruptedException {

        // Create a container for remoting
        ArgumentListBuilder args = new ArgumentListBuilder()
            .add("create", "--interactive")

            // We disable container logging to sdout as we rely on this one as transport for jenkins remoting
            .add("--log-driver=none")

            .add("--env", "TMPDIR="+ SLAVE_ROOT+".tmp")
            .add("--user", "10000:10000")
            .add("--volume", volume+":"+ SLAVE_ROOT)
            .add(image)
            .add("java")
            // set TMP directory within the /home/jenkins/ volume so it can be shared with other containers
            .add("-Djava.io.tmpdir="+ SLAVE_ROOT+".tmp")
            .add("-jar").add(SLAVE_ROOT+"slave.jar");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        String containerId = out.toString(UTF_8).trim();

        if (status != 0) {
            throw new IOException("Failed to create docker image");
        }

        // Inject current slave.jar to ensure adequate version running
        putFileContent(launcher, containerId, DockerSlave.SLAVE_ROOT, "slave.jar", new Slave.JnlpJar("slave.jar").readFully());
        Container remotingContainer = new Container(image, containerId);

        // Run container in interactive mode to establish channel over stdin/stdout
        args = new ArgumentListBuilder()
                .add("start")
                .add("--interactive", "--attach", remotingContainer.getId());
        prependArgs(args);
        new CommandLauncher(args.toString(), dockerHost.getEnvironment()).launch(computer, listener);
        return remotingContainer;
    }

    @Override
    public Container launchBuildContainer(TaskListener listener, String image, Container remotingContainer, List<Hint> hints) throws IOException, InterruptedException {
        Container buildContainer = new Container(image);
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("create")
                .add("--env", "TMPDIR="+SLAVE_ROOT+".tmp")
                .add("--workdir", SLAVE_ROOT)
                .add("--volumes-from", remotingContainer.getId())
                .add("--net=container:" + remotingContainer.getId())
                .add("--ipc=container:" + remotingContainer.getId())
                .add("--user", "10000:10000");

        applyHints(hints, args);

        args.add(buildContainer.getImageName());

        args.add("/trampoline", "wait");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        final String containerId = out.toString(UTF_8).trim();
        buildContainer.setId(containerId);

        if (status != 0) {
            throw new IOException("Failed to run docker image");
        }

        injectJenkinsUnixGroup(launcher, containerId);
        injectJenkinsUnixUser(launcher, containerId);
        injectTrampoline(launcher, containerId);

        status = launchDockerCLI(launcher, new ArgumentListBuilder()
                .add("start", containerId)).stdout(out).stderr(launcher.getListener().getLogger()).join();

        if (status != 0) {
            throw new IOException("Failed to run docker image");
        }

        return buildContainer;
    }

    private void applyHints(List<Hint> hints, ArgumentListBuilder args) {
        for (Hint hint : hints) {
            if (hint instanceof MemoryHint) {
                args.add("-m", ((MemoryHint) hint).getMemory());
            } else if (hint instanceof VolumeHint) {
                args.add("-v", ((VolumeHint) hint).getVolume());
            } else {
                // unsupported hint, just ignored
            }
        }
    }

    protected void injectJenkinsUnixGroup(Launcher launcher, String containerId) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        getFileContent(launcher, containerId, "/etc/group", out);
        out.write("jenkins:x:10000:\n".getBytes(StandardCharsets.UTF_8));
        putFileContent(launcher, containerId, "/etc", "group", out.toByteArray());
    }

    protected void injectJenkinsUnixUser(Launcher launcher, String containerId) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        getFileContent(launcher, containerId, "/etc/passwd", out);
        out.write("jenkins:x:10000:10000::/home/jenkins:/bin/false\n".getBytes(StandardCharsets.UTF_8));
        putFileContent(launcher, containerId, "/etc", "passwd", out.toByteArray());
    }

    protected void injectTrampoline(Launcher launcher, String containerId) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOUtils.copy(getClass().getResourceAsStream("/it/dockins/dockerslaves/trampoline"),out);
        putFileContent(launcher, containerId, "/", "trampoline", out.toByteArray(), 555);
    }

    protected void getFileContent(Launcher launcher, String containerId, String filename, OutputStream outputStream) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("cp", containerId + ":" + filename, "-");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        if (status != 0) {
            throw new IOException("Failed to get file");
        }

        TarInputStream tar = new TarInputStream(new ByteArrayInputStream(out.toByteArray()));
        tar.getNextEntry();
        tar.copyEntryContents(outputStream);
        tar.close();
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

        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("cp", "-", containerId + ":" + path);

        return launchDockerCLI(launcher, args)
                .stdin(new ByteArrayInputStream(out.toByteArray()))
                .stderr(launcher.getListener().getLogger()).join();
    }

    @Override
    public Proc execInContainer(TaskListener listener, String containerId, Launcher.ProcStarter starter) throws IOException, InterruptedException {
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

        Launcher launcher = new Launcher.LocalLauncher(listener);
        Launcher.ProcStarter procStarter = launchDockerCLI(launcher, args);

        if (starter.stdout() != null) {
            procStarter.stdout(starter.stdout());
        }

        return procStarter.start();
    }

    @Override
    public void removeContainer(TaskListener listener, Container instance) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("rm", "-f", instance.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        if (status != 0) {
            throw new IOException("Failed to remove container " + instance.getId());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ProvisionQueueListener.class.getName());

    @Override
    public Container launchSideContainer(TaskListener listener, String image, Container remotingContainer, List<Hint> hints) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("create")
                .add("--volumes-from", remotingContainer.getId())
                .add("--net=container:" + remotingContainer.getId())
                .add("--ipc=container:" + remotingContainer.getId());

        applyHints(hints, args);

        args.add(image);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        final String containerId = out.toString(UTF_8).trim();


        if (status != 0) {
            throw new IOException("Failed to run docker image");
        }

        launchDockerCLI(launcher, new ArgumentListBuilder()
                .add("start", containerId)).start();

        return new Container(image, containerId);
    }

    @Override
    public void pullImage(TaskListener listener, String image) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("pull")
                .add(image);

        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status =  launchDockerCLI(launcher, args)
                .stdout(launcher.getListener().getLogger()).join();

        if (status != 0) {
            throw new IOException("Failed to pull image " + image);
        }
    }

    @Override
    public boolean checkImageExists(TaskListener listener, String image) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("inspect")
                .add("-f", "'{{.Id}}'")
                .add(image);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        return launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join() == 0;
    }

    @Override
    public void buildDockerfile(TaskListener listener, String dockerfilePath, String tag, boolean pull)  throws IOException, InterruptedException {
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

        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(launcher.getListener().getLogger()).join();

        if (status != 0) {
            throw new IOException("Failed to build docker image from Dockerfile " + dockerfilePath);
        }
    }

    @Override
    public String serverVersion(TaskListener listener) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("version", "-f", "{{.Server.Version}}");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        final String version = out.toString(UTF_8).trim();

        if (status != 0) {
            throw new IOException("Failed to connect to docker API");
        }

        return version;
    }

    VersionNumber SWARM = new VersionNumber("1.12");
    VersionNumber INFO_FORMAT = new VersionNumber("1.13");

    public boolean usesSwarmMode(TaskListener listener) throws IOException, InterruptedException {
        if (version.isOlderThan(SWARM)) return false;

        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("docker", "info");
        if (!version.isOlderThan(INFO_FORMAT)) {
            args.add("--format", "{{.Swarm}}");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        if (status != 0) {
            throw new IOException("Failed to connect to docker API");
        }

        return out.toString(UTF_8).contains("Swarm: active");
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

    public static final String UTF_8 = StandardCharsets.UTF_8.name();
}
