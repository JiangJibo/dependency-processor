package com.shulie.agent.dependency;

import com.shulie.agent.dependency.constant.DependencyRepositoryConfig;
import com.shulie.agent.dependency.entity.Dependency;
import com.shulie.agent.dependency.processor.LocalRepositoryManager;
import com.shulie.agent.dependency.processor.PomFileReader;
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
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
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

    private Path moduleConfig;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info(String.format("[maven-plugin:dependency-processor] parse %s to collect provided dependencies, includeArtifacts:%s, excludeArtifacts:%s, includeGroups:%s, excludeGroups:%s",
                moduleHome + File.separator + "pom.xml", includeArtifacts, excludeArtifacts, includeGroups, excludeGroups));
        List<Dependency> dependencies;
        try {
            dependencies = PomFileReader.extractPomDependencies(null, Paths.get(moduleHome, "pom.xml").toFile().getAbsolutePath(), null, false, false).getValue();
        } catch (Exception e) {
            getLog().error(String.format("[maven-plugin:dependency-processor] 'dependency-processor' parse %s occur q exception!!", moduleHome + File.separator + "pom.xml"), e);
            return;
        }

        List<String> includeGroupList = isNotEmpty(includeGroups) ? Arrays.asList(includeGroups.split(",")) : new ArrayList<>();
        List<String> excludeGroupList = isNotEmpty(excludeGroups) ? Arrays.asList(excludeGroups.split(",")) : new ArrayList<>();
        List<String> includeArtifactList = isNotEmpty(includeArtifacts) ? Arrays.asList(includeArtifacts.split(",")) : new ArrayList<>();
        List<String> excludeArtifactList = isNotEmpty(excludeArtifacts) ? Arrays.asList(excludeArtifacts.split(",")) : new ArrayList<>();
        // 收集properties
        // 过滤框架依赖和非provide依赖
        dependencies = dependencies.stream().filter(new Predicate<Dependency>() {
            @Override
            public boolean test(Dependency dependency) {
                boolean provided = "provided".equals(dependency.scope);
                boolean system = "system".equals(dependency.scope);
                boolean excluded = excludeGroupList.contains(dependency.groupId) || excludeArtifactList.contains(dependency.artifactId);
                return includeGroupList.contains(dependency.groupId) || includeArtifactList.contains(dependency.artifactId) || (!excluded && !system && provided);
            }
        }).collect(Collectors.toList());

        // 找到本地maven仓库
        String repositoryPath;
        try {
            repositoryPath = LocalRepositoryManager.findLocalRepositoryByDefault();
            if (repositoryPath == null) {
                repositoryPath = LocalRepositoryManager.findLocalRepositoryFromConfig();
            }
            DependencyRepositoryConfig.localRepositoryPath = repositoryPath;

            List<Dependency> deepDependencies = PomFileReader.collectDependenciesDeeply(dependencies);
            dependencies.addAll(deepDependencies);
        } catch (IOException e) {
            getLog().error(String.format("[maven-plugin:dependency-processor] execute goal 'DependencyCollector' parsing %s occur q exception!!", moduleHome + File.separator + "pom.xml"), e);
            return;
        }

        Map<String, Dependency> dependencyMap = new HashMap<>();
        for (Dependency dependency : dependencies) {
            String key = dependency.groupId + ":" + dependency.artifactId;
            if (dependencyMap.containsKey(key)) {
                Dependency old = dependencyMap.get(key);
                boolean needRefresh = dependency.deep < old.deep || (dependency.deep == old.deep && dependency.time < old.time);
                if (needRefresh) {
                    dependencyMap.put(key, dependency);
                }
            } else {
                dependencyMap.put(key, dependency);
            }
        }

        Collection<Dependency> collectDependencies = dependencyMap.values();

        printDependency(collectDependencies);

        moduleConfig = Paths.get(moduleHome, "src", "main", "resources", "module.config");
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
        } catch (IOException e) {
            getLog().error(String.format("[maven-plugin:dependency-processor] execute goal 'DependencyCollector' parsing %s occur q exception!", moduleHome + File.separator + "pom.xml", e));
        }

    }


    private boolean isEmpty(String text) {
        return text == null || text.trim().length() == 0;
    }

    private boolean isNotEmpty(String text) {
        return !isEmpty(text);
    }

    private void printDependency(Collection<Dependency> dependencies) {
        List<String> collect = dependencies.stream().map(new Function<Dependency, String>() {
            @Override
            public String apply(Dependency dependency) {
                return dependency.groupId + ":" + dependency.artifactId + ":" + dependency.version;
            }
        }).collect(Collectors.toList());

        getLog().info(String.format("[maven-plugin:dependency-processor] parse %s collected needed dependencies: %s", moduleHome + File.separator + "pom.xml", collect));
    }

    private String joinDependenciesInfo(Collection<Dependency> dependencies) {
        return dependencies.stream().map(new Function<Dependency, String>() {
            @Override
            public String apply(Dependency dependency) {
                return dependency.artifactId;
            }
        }).collect(Collectors.joining(","));
    }

}
