package it.dockins.dockerslaves;

import hudson.Extension;
import hudson.model.Job;
import hudson.util.FormValidation;
import it.dockins.dockerslaves.spi.DockerHostConfig;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;
import it.dockins.dockerslaves.spi.DockerHostSource;
import it.dockins.dockerslaves.spi.DockerHostSourceDescriptor;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DefaultDockerHostSource extends DockerHostSource {

    private final DockerServerEndpoint dockerServerEndpoint;

    private final String hyperAccessKey;
    private final String hyperSecretKey;

    public DefaultDockerHostSource() {
        this(new DockerServerEndpoint(null, null), null, null);
    }

    @DataBoundConstructor
    public DefaultDockerHostSource(DockerServerEndpoint dockerServerEndpoint, String hyperAccessKey, String hyperSecretKey) {
        this.dockerServerEndpoint = dockerServerEndpoint;
        this.hyperAccessKey = hyperAccessKey;
        this.hyperSecretKey = hyperSecretKey;
        this.SaveConfig(hyperAccessKey, hyperSecretKey);
    }

    private void SaveConfig(String hyperAccessKey, String hyperSecretKey) {
        if (hyperAccessKey == null || hyperSecretKey == null) {
            return;
        }

        String jsonStr = "{\"clouds\": {" +
                "\"tcp://us-west-1.hyper.sh:443\": {" +
                "\"accesskey\": " + "\"" + hyperAccessKey + "\"," +
                "\"secretkey\": " + "\"" + hyperSecretKey + "\"" +
                "}" +
                "}" +
                "}";
        BufferedWriter writer = null;
        String configPath;
        String jenkinsHome = System.getenv("HUDSON_HOME");

        if (jenkinsHome == null) {
            String home = System.getenv("HOME");
            configPath = home + "/.hyper/config.json";
            File hyperPath = new File(home + "/.hyper");
            try {
                if (!hyperPath.exists()) {
                    hyperPath.mkdir();
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } else {
            File hyperPath = new File(jenkinsHome +"/.hyper");
            try {
                if (!hyperPath.exists()) {hyperPath.mkdir();
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            configPath = jenkinsHome + "/.hyper/config.json";
        }


        File config = new File(configPath);
        if(!config.exists()){
            try {
                config.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            writer = new BufferedWriter(new FileWriter(config));
            writer.write(jsonStr);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if(writer != null){
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public DockerServerEndpoint getDockerServerEndpoint() {
        return dockerServerEndpoint;
    }

    @Override
    public DockerHostConfig getDockerHost(Job job) throws IOException, InterruptedException {
        return new DockerHostConfig(dockerServerEndpoint, hyperAccessKey, hyperSecretKey, job);
    }

    @Extension
    public static class DescriptorImp extends DockerHostSourceDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Standalone Docker daemon / Docker Swarm cluster";
        }
    }
}
