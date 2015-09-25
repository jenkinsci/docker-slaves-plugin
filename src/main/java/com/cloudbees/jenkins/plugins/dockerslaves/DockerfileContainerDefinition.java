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

package com.cloudbees.jenkins.plugins.dockerslaves;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.UUID;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerfileContainerDefinition extends ContainerDefinition {

    private final String dockerfile;

    private final String contextPath;

    private transient String image;

    @DataBoundConstructor
    public DockerfileContainerDefinition(String contextPath, String dockerfile) {
        this.contextPath = contextPath;
        this.dockerfile = dockerfile;
    }

    public String getDockerfile() {
        return dockerfile;
    }

    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getImage(TaskListener listener) throws IOException, InterruptedException {
        if (image != null) return image;
        String tag = Long.toHexString(System.nanoTime());


        // TODO we need to run this command from a laucnher which can run docker cli, is configured to access dockerhost,
        // and has workspace mounted. Not sure yet.

        final Launcher launcher = null;
        int status = launcher.launch()
                .cmds("docker", "build", "-t", tag, "-f", dockerfile, contextPath)
                .join();
        if (status != 0) {
            throw new IOException("Failed to build image from Dockerfile");
        }
        this.image = tag;
        return tag;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ContainerDefinition> {

        @Override
        public String getDisplayName() {
            return "Build Dockerfile";
        }
    }
}
