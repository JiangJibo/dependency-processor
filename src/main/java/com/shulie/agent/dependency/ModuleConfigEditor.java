package com.shulie.agent.dependency;

import com.shulie.agent.dependency.entity.Dependency;
import com.shulie.agent.dependency.util.DependencyInfoCollector;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mojo(name = "ModuleConfigEdit")
public class ModuleConfigEditor extends AbstractMojo {

    @Parameter(property = "moduleHome")
    private String moduleHome;

    @Parameter(property = "includeArtifacts")
    private String includeArtifacts;

    @Parameter(property = "excludeArtifacts")
    private String excludeArtifacts;

    @Parameter(property = "includeGroups")
    private String includeGroups;

    @Parameter(property = "excludeGroups")
    private String excludeGroups;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        Collection<Dependency> collectDependencies = DependencyInfoCollector.collectDependencies(this, moduleHome, includeArtifacts, excludeArtifacts, includeGroups, excludeGroups);

        Path moduleConfig = Paths.get(moduleHome, "src", "main", "resources", "module.config");
        try {
            List<String> lines = Files.readAllLines(moduleConfig);
            String replacedLine = String.format("import-artifacts=%s", joinDependenciesInfo(collectDependencies));

            int index = lines.size() - 1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().startsWith("import-artifacts")) {
                    index = i;
                }
            }
            lines.set(index, replacedLine);
            Files.write(moduleConfig, lines);
        } catch (Exception e) {
            e.printStackTrace();
            getLog().error(String.format("[maven-plugin:dependency-processor] execute goal 'ModuleConfigEdit' parsing %s occur a exception!", moduleHome + File.separator + "pom.xml", e.getMessage()));
        }

    }


    private String joinDependenciesInfo(Collection<Dependency> dependencies) {
        return dependencies.stream().map(new Function<Dependency, String>() {
            @Override
            public String apply(Dependency dependency) {
                if (!dependency.version.contains("-")) {
                    return dependency.artifactId;
                }
                return dependency.artifactId + "-" + dependency.version.substring(0, dependency.version.lastIndexOf("-"));
            }
        }).collect(Collectors.joining(","));
    }

}
