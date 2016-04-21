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

package xyz.quoidneufdocker.jenkins.dockerslaves;

import hudson.Launcher;
import hudson.Proc;
import hudson.model.Item;
import hudson.model.Slave;
import hudson.org.apache.tools.tar.TarOutputStream;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterial;

import java.io.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author <a href="mailto:yoann.dubreuil@gmail.com">Yoann Dubreuil</a>
 */
public class DockerDriver implements Closeable {

    private final boolean verbose;

    final DockerServerEndpoint dockerHost;

    final KeyMaterial dockerEnv;

    public DockerDriver(DockerServerEndpoint dockerHost, Item context) throws IOException, InterruptedException {
        this.dockerHost = dockerHost;
        dockerEnv = dockerHost.newKeyMaterialFactory(context, Jenkins.getActiveInstance().getChannel()).materialize();
        verbose = true;
    }

    @Override
    public void close() throws IOException {
        dockerEnv.close();
    }

    public boolean hasContainer(Launcher launcher, String id) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(id)) {
            return false;
        }

        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("inspect", "-f", "'{{.Id}}'", id);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        if (status != 0) {
            return false;
        } else {
            return true;
        }
    }

    public ContainerInstance createRemotingContainer(Launcher launcher, String image) throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("create", "--interactive")

                // We disable container logging to sdout as we rely on this one as transport for jenkins remoting
                .add("--log-driver=none")

                .add("--env", "TMPDIR=/home/jenkins/.tmp")
                .add("--user", "10000:10000")
                .add(image)
                .add("java")

                // set TMP directory within the /home/jenkins/ volume so it can be shared with other containers
                .add("-Djava.io.tmpdir=/home/jenkins/.tmp")
                .add("-jar").add("/home/jenkins/slave.jar");

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        String containerId = out.toString("UTF-8").trim();

        if (status != 0) {
            throw new IOException("Failed to run docker image");
        }

        putFileContent(launcher, containerId, "/home/jenkins", "slave.jar", new Slave.JnlpJar("slave.jar").readFully());
        return new ContainerInstance(image, containerId);
    }

    public ContainerInstance createBuildContainer(Launcher launcher, String image, ContainerInstance remotingContainer) throws IOException, InterruptedException {
        ContainerInstance buildContainer = new ContainerInstance(image);
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("create")
                .add("--env", "TMPDIR=/home/jenkins/.tmp")
                .add("--workdir", "/home/jenkins")
                .add("--volumes-from", remotingContainer.getId())
                .add("--net=container:" + remotingContainer.getId())
                .add("--ipc=container:" + remotingContainer.getId())
                .add("--user", "10000:10000");

        args.add(buildContainer.getImageName());

        args.add("/trampoline", "wait");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        final String containerId = out.toString("UTF-8").trim();
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
        IOUtils.copy(getClass().getResourceAsStream("/xyz/quoidneufdocker/jenkins/dockerslaves/trampoline"),out);
        putFileContent(launcher, containerId, "/", "trampoline", out.toByteArray(), 555);
    }

    protected void getFileContent(Launcher launcher, String containerId, String filename, OutputStream outputStream) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("cp", containerId + ":" + filename, "-");

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

    public int removeContainer(Launcher launcher, ContainerInstance instance) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("rm", "-f", instance.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int status = launchDockerCLI(launcher, args)
                .stdout(out).stderr(launcher.getListener().getLogger()).join();

        return status;
    }

    private static final Logger LOGGER = Logger.getLogger(ProvisionQueueListener.class.getName());

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

    public boolean checkImageExists(Launcher launcher, String image) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("inspect")
                .add("-f", "'{{.Id}}'")
                .add(image);

        return launchDockerCLI(launcher, args)
                .stdout(launcher.getListener().getLogger()).join() == 0;
    }

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
        if (dockerHost.getUri() != null) {
            args.prepend("-H", dockerHost.getUri());
        } else {
            LOGGER.log(Level.FINE, "no specified docker host");
        }

        args.prepend("docker");
    }

    private Launcher.ProcStarter launchDockerCLI(Launcher launcher, ArgumentListBuilder args) {
        prependArgs(args);

        return launcher.launch()
                .envs(dockerEnv.env())
                .cmds(args)
                .quiet(!verbose);
    }
}
