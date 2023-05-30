package com.shulie.agent.dependency.util;

import com.shulie.agent.dependency.constant.DependencyRepositoryConfig;
import com.shulie.agent.dependency.entity.Dependency;
import org.apache.maven.plugin.Mojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DependencyInfoCollector {

    public static Collection<Dependency> collectDependencies(Mojo mojo, String moduleHome, String includeArtifacts, String excludeArtifacts, String includeGroups, String excludeGroups) {
        mojo.getLog().info(String.format("[maven-plugin:dependency-processor] parse %s to collect provided dependencies, includeArtifacts:%s, excludeArtifacts:%s, includeGroups:%s, excludeGroups:%s",
                moduleHome + File.separator + "pom.xml", includeArtifacts, excludeArtifacts, includeGroups, excludeGroups));
        List<Dependency> dependencies;
        try {
            dependencies = PomFileReader.extractPomDependencies(null, Paths.get(moduleHome, "pom.xml").toFile().getAbsolutePath(), null, false, false).getValue();
        } catch (Exception e) {
            mojo.getLog().error(String.format("[maven-plugin:dependency-processor] 'dependency-processor' parse %s occur q exception!!", moduleHome + File.separator + "pom.xml"), e);
            return null;
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
            mojo.getLog().error(String.format("[maven-plugin:dependency-processor] execute goal 'DependencyCollector' parsing %s occur q exception!!", moduleHome + File.separator + "pom.xml"), e);
            return null;
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
        printDependency(mojo, moduleHome, collectDependencies);

        return collectDependencies;
    }


    private static boolean isEmpty(String text) {
        return text == null || text.trim().length() == 0;
    }

    private static boolean isNotEmpty(String text) {
        return !isEmpty(text);
    }

    private static void printDependency(Mojo mojo, String moduleHome, Collection<Dependency> dependencies) {
        List<String> collect = dependencies.stream().map(new Function<Dependency, String>() {
            @Override
            public String apply(Dependency dependency) {
                return dependency.groupId + ":" + dependency.artifactId + ":" + dependency.version;
            }
        }).collect(Collectors.toList());

        mojo.getLog().info(String.format("[maven-plugin:dependency-processor] parse %s collected needed dependencies: %s", moduleHome + File.separator + "pom.xml", collect));
    }

}
