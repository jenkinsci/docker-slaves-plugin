package xyz.quoidneufdocker.jenkins.dockerslaves;

import xyz.quoidneufdocker.jenkins.dockerslaves.spec.ContainerSetDefinition;
import xyz.quoidneufdocker.jenkins.dockerslaves.spec.ImageIdContainerDefinition;
import hudson.model.FreeStyleProject;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.junit.Assert.*;

import static org.hamcrest.Matchers.*;

public class ConfigTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void minimalXmlPropertyDefinitionYieldsSaneDefaultValues() throws Exception {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem("minimal");
        ContainerSetDefinition definition = p.getProperty(ContainerSetDefinition.class);

        assertThat(definition.getSideContainers(), empty());
        assertThat(definition.getBuildHostImage(), instanceOf(ImageIdContainerDefinition.class));
    }
}
