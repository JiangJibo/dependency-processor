package com.shulie.agent.dependency;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "DependencyJarCollect")
public class DependencyJarCollector extends AbstractMojo {

    @Parameter(property = "moduleHome")
    private String moduleHome;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

    }

}
