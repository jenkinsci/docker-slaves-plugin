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

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import it.dockins.dockerslaves.spi.DockerDriver;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerfileContainerDefinition extends ContainerDefinition {

    private final String dockerfile;

    private final String contextPath;

    private final boolean forcePull;

    private transient String image;

    @DataBoundConstructor
    public DockerfileContainerDefinition(String contextPath, String dockerfile, boolean forcePull) {
        this.contextPath = contextPath;
        this.dockerfile = dockerfile;
        this.forcePull = forcePull;
    }

    public String getDockerfile() {
        return dockerfile;
    }

    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getImage(DockerDriver driver, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        if (image != null) return image;
        String tag = Long.toHexString(System.nanoTime());

        final FilePath pathToContext = workspace.child(contextPath);
        if (!pathToContext.exists()) {
            throw new IOException(pathToContext.getRemote() + " does not exists.");
        }

        final FilePath pathToDockerfile = pathToContext.child(dockerfile);
        if (!pathToDockerfile.exists()) {
            throw new IOException( pathToContext.getRemote() + " does not exists.");
        }

        final File context = Util.createTempDir();
        pathToContext.copyRecursiveTo(new FilePath(context));
        pathToDockerfile.copyTo(new FilePath(new File(context, "Dockerfile")));

        driver.buildDockerfile(listener, context.getAbsolutePath(), tag, forcePull);
        Util.deleteRecursive(context);
        this.image = tag;
        return tag;
    }

    public boolean isForcePull() {
        return forcePull;
    }

    @Extension
    public static class DescriptorImpl extends ContainerDefinitionDescriptor {

        @Override
        public String getDisplayName() {
            return "Build Dockerfile";
        }
    }


    private static MasterToSlaveFileCallable<byte[]> FILECONTENT = new MasterToSlaveFileCallable<byte[]>() {
        @Override
        public byte[] invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return FileUtils.readFileToByteArray(f);
        }
    };
}
