package it.dockins.dockerslaves;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import it.dockins.dockerslaves.spec.SideContainerDefinition;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class ContainerSpecEnvironmentContributor extends EnvironmentContributor {

    @Override
    public void buildEnvironmentFor(@NonNull Run r, @NonNull EnvVars envs, @NonNull TaskListener listener) throws IOException, InterruptedException {
        final Job job = r.getParent();
        final ContainerSetDefinition property = (ContainerSetDefinition) job.getProperty(ContainerSetDefinition.class);
        if (property == null) return;

        property.getBuildHostImage().setupEnvironment(envs);
        for (SideContainerDefinition sidecar : property.getSideContainers()) {
            sidecar.getSpec().setupEnvironment(envs);
        }
    }
}
