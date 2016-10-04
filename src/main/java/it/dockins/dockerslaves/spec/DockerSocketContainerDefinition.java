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

package it.dockins.dockerslaves.spec;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import it.dockins.dockerslaves.spi.DockerDriver;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Configure a sidecar container to expose the host's docker socket inside container set,
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerSocketContainerDefinition extends ContainerDefinition {

    @DataBoundConstructor
    public DockerSocketContainerDefinition() {
    }

    @Override
    public String getImage(DockerDriver driver, FilePath workspace, TaskListener listener) {
        return "dockins/dockersock";
    }

    public List<String> getMounts() {
        return Collections.singletonList("/var/run/docker.sock:/var/run/docker.sock");
    }

    @Override
    public void setupEnvironment(EnvVars env) {
        env.put("DOCKER_HOST", "tcp://localhost:2375");
    }

    @Extension(ordinal = -666)
    public static class DescriptorImpl extends Descriptor<ContainerDefinition> {

        @Override
        public String getDisplayName() {
            return "Access host's docker daemon";
        }
    }
}
