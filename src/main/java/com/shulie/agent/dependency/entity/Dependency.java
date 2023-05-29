package com.shulie.agent.dependency.entity;

import java.util.List;

public class Dependency {

    public Dependency importBy;
    public String groupId;
    public String artifactId;
    public String version;
    public String scope = "compile";
    public String optional = "false";
    public int deep = 0;
    public long time = System.nanoTime();
    public List<Exclusion> exclusions;

    public static class Exclusion {
        public String groupId;
        public String artifactId;
    }
}
