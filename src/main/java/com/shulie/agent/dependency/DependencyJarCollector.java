package com.shulie.agent.dependency;

import com.shulie.agent.dependency.constant.DependencyRepositoryConfig;
import com.shulie.agent.dependency.entity.Dependency;
import com.shulie.agent.dependency.util.DependencyInfoCollector;
import com.shulie.agent.dependency.util.LocalRepositoryManager;
import com.shulie.agent.dependency.util.PomFileReader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mojo(name = "DependencyJarCollect")
public class DependencyJarCollector extends AbstractMojo {

    private String moduleHome;

    private String includeArtifacts;

    private String excludeArtifacts;

    private String includeGroups;

    private String excludeGroups;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        moduleHome = System.getProperty("user.dir");
        DependencyRepositoryConfig.currentModulePath = moduleHome;

        Map<String, String> configurations;
        try {
            configurations = PomFileReader.extractPluginConfigurations(moduleHome + File.separator + "pom.xml");
        } catch (IOException e) {
            getLog().error(String.format("[maven-plugin:dependency-processor] execute goal 'DependencyJarCollect' parsing %s extract plugin configurations occur a exception!", moduleHome + File.separator + "pom.xml", e.getMessage()));
            return;
        }
        Collection<Dependency> collectDependencies = DependencyInfoCollector.collectDependencies(this, moduleHome, configurations.get("includeArtifacts"), configurations.get("excludeArtifacts"), configurations.get("includeGroups"), configurations.get("excludeGroups"));
        // 收集依赖jar包
        List<File> jarFiles = collectDependencies.stream().map(dependency -> LocalRepositoryManager.findDependencyJarFile(dependency)).collect(Collectors.toList());
        // 拷贝jar包到lib目录
        try {
            LocalRepositoryManager.copyDependencyJarFilesToLib(jarFiles);
        } catch (Exception e) {
            e.printStackTrace();
            getLog().error(String.format("[maven-plugin:dependency-processor] execute goal 'DependencyJarCollect' for module %s to copy needed dependency jar occur a exception!!", moduleHome, e.getMessage()));
        }
    }


}
