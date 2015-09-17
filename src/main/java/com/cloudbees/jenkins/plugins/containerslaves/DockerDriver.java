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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerDriver {

    private final boolean verbose;

    String host;

    public DockerDriver(String host) {
        this.host = host;
        verbose = true;
    }

    public String createRemotingContainer(Launcher launcher, String image) throws IOException, InterruptedException {
        ArgumentListBuilder args = dockerCommand()
                .add("create", "--interactive")

                // We disable container logging to sdout as we rely on this one as transport for jenkins remoting
                .add("--log-driver=none")

                .add(image).add("java")

                // set TMP directory within the /home/jenkins/ volume so it can be shared with other containers
                .add("-Djava.io.tmpdir=/home/jenkins/.tmp")
                .add("-jar").add("/usr/share/jenkins/slave.jar");

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int status = launcher.launch()
                .cmds(args)
                .stdout(out).quiet(!verbose).stderr(launcher.getListener().getLogger()).join();

        if (status != 0) {
            throw new IOException("Failed to run docker image");
        }

        String container = out.toString("UTF-8").trim();
        return container;
    }

    private ArgumentListBuilder dockerCommand() {
        return new ArgumentListBuilder().add("docker");
    }

    private static final Logger LOGGER = Logger.getLogger(ProvisionQueueListener.class.getName());

}
