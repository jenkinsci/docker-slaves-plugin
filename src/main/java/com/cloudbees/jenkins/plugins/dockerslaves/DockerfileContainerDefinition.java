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
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.org.apache.tools.tar.TarOutputStream;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

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
    public String getImage(Launcher.ProcStarter procStarter, TaskListener listener) throws IOException, InterruptedException {
        if (image != null) return image;
        String tag = Long.toHexString(System.nanoTime());


        final FilePath workspace = procStarter.pwd();
        FilePath contextRoot = workspace.child(contextPath);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TarOutputStream tar = new TarOutputStream(new GZIPOutputStream(out));

        final byte[] b = contextRoot.child(dockerfile).act(FILECONTENT);
        TarEntry entry = new TarEntry("Dockerfile");
        entry.setUserId(0);
        entry.setGroupId(0);
        entry.setSize(b.length);

        tar.putNextEntry(entry);
        tar.write(b);
        tar.closeEntry();
        tar.close();
        out.close();

        // TODO add context files to tar.gz

        final Launcher launcher = new Launcher.LocalLauncher(listener);
        int status = launcher.launch()
                .cmds("docker", "build", "-t", tag, "-")
                .stdin(new ByteArrayInputStream(out.toByteArray()))
                .join();
        if (status != 0) {
            throw new IOException("Failed to build image from Dockerfile "+dockerfile);
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


    private static MasterToSlaveFileCallable<byte[]> FILECONTENT = new MasterToSlaveFileCallable<byte[]>() {
        @Override
        public byte[] invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return FileUtils.readFileToByteArray(f);
        }
    };
}
