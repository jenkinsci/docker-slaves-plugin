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

package com.cloudbees.jenkins.plugins.containerslaves;

import hudson.Launcher;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author <a href="mailto:yoann.dubreuil@gmail.com">Yoann Dubreuil</a>
 */
public class DockerDriver {

    private final boolean verbose;

    String host;

    public DockerDriver(String host) {
        this.host = host;
        verbose = true;
    }

    public boolean hasContainer(Launcher launcher, ContainerInstance instance) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(instance.getId())) {
            return false;
        }

        ArgumentListBuilder args = dockerCommand()
                .add("inspect", "-f", "'{{.Id}}'", instance.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int status = launcher.launch()
                .cmds(args)
                .stdout(out).quiet(!verbose).stderr(launcher.getListener().getLogger()).join();

        if (status != 0) {
            return false;
        } else {
            return true;
        }
    }

    public void createRemotingContainer(Launcher launcher, ContainerInstance remotingContainer) throws IOException, InterruptedException {
        ArgumentListBuilder args = dockerCommand()
                .add("create", "--interactive")

                // We disable container logging to sdout as we rely on this one as transport for jenkins remoting
                .add("--log-driver=none")

                .add(remotingContainer.getImageName()).add("java")

                // set TMP directory within the /home/jenkins/ volume so it can be shared with other containers
                .add("-Djava.io.tmpdir=/home/jenkins/.tmp")
                .add("-jar").add("/usr/share/jenkins/slave.jar");

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int status = launcher.launch()
                .cmds(args)
                .stdout(out).quiet(!verbose).stderr(launcher.getListener().getLogger()).join();

        String containerId = out.toString("UTF-8").trim();
        remotingContainer.setId(containerId);

        if (status != 0) {
            throw new IOException("Failed to run docker image");
        }
    }

    public void createBuildContainer(Launcher launcher, ContainerInstance buildContainer, ContainerInstance remotingContainer, Launcher.ProcStarter starter) throws IOException, InterruptedException {
        ArgumentListBuilder args = dockerCommand()
                .add("create", "--tty")
                // We disable container logging to sdout as we rely on this one as transport for jenkins remoting
                //.add("--log-driver=none")
                .add("--workdir",  starter.pwd().toString())
                .add("--volumes-from", remotingContainer.getId())
                .add("--net=container:" + remotingContainer.getId())
                .add("--user", "10000:10000")
                .add(buildContainer.getImageName());

        List<String> originalCmds = starter.cmds();
        boolean[] originalMask = starter.masks();
        for (int i = 0; i < originalCmds.size(); i++) {
            boolean masked = originalMask == null ? false : i < originalMask.length ? originalMask[i] : false;
            args.add(originalCmds.get(i), masked);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds(args)
                .stdout(out).quiet(!verbose).stderr(launcher.getListener().getLogger()).join();

        final String containerId = out.toString("UTF-8").trim();
        buildContainer.setId(containerId);

        if (status != 0) {
            throw new IOException("Failed to run docker image");
        }
    }

    protected int getFileContent(Launcher launcher, String containerId, String filename, OutputStream content) throws IOException, InterruptedException {
        ArgumentListBuilder args = dockerCommand()
                .add("cp", containerId + ":" + filename, "-");

        return launcher.launch()
                .cmds(args)
                .stdout(content).quiet(!verbose).stderr(launcher.getListener().getLogger()).join();
    }

    protected int putFileContent(Launcher launcher, String containerId, String filename, InputStream content) throws IOException, InterruptedException {
        ArgumentListBuilder args = dockerCommand()
                .add("cp", "-", containerId + ":" + filename);

        return launcher.launch()
                .cmds(args).stdin(content).quiet(!verbose).stderr(launcher.getListener().getLogger()).join();
    }

    public Launcher.ProcStarter runContainer(Launcher launcher, String containerId) throws IOException, InterruptedException {
        ArgumentListBuilder args = dockerCommand()
                .add("start", "-ia", containerId);

        return launcher.launch().cmds(args);
    }

    public int removeContainer(Launcher launcher, ContainerInstance instance) throws IOException, InterruptedException {
        ArgumentListBuilder args = dockerCommand()
                .add("rm", "-f", instance.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int status = launcher.launch()
                .cmds(args)
                .stdout(out).quiet(!verbose).stderr(launcher.getListener().getLogger()).join();

        return status;
    }

    private ArgumentListBuilder dockerCommand() {
        return new ArgumentListBuilder().add("docker");
    }

    private static final Logger LOGGER = Logger.getLogger(ProvisionQueueListener.class.getName());

    public void launchSideContainer(Launcher launcher, ContainerInstance instance, ContainerInstance remotingContainer) throws IOException, InterruptedException {
        ArgumentListBuilder args = dockerCommand()
                .add("run", "--tty")
                .add("--volumes-from", remotingContainer.getId())
                .add("--net=container:" + remotingContainer.getId())
                .add(instance.getImageName());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds(args)
                .stdout(out).quiet(!verbose).stderr(launcher.getListener().getLogger()).join();

        final String containerId = out.toString("UTF-8").trim();
        instance.setId(containerId);

        if (status != 0) {
            throw new IOException("Failed to run docker image");
        }
    }
}
