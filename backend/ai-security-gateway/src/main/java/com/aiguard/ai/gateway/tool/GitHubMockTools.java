package com.aiguard.ai.gateway.tool;

import com.aiguard.ai.gateway.iam.UserRole;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

public final class GitHubMockTools {
    private GitHubMockTools() {}
    private static final String REPOSITORY = "imvaibhavm/ai-iam-gateway";
    private static final String REPOSITORY_URL = "https://github.com/" + REPOSITORY;
    private static Set<UserRole> roles() { return Set.of(UserRole.ENGINEER, UserRole.ADMIN); }

    @Component
    public static class SearchCode implements ToolHandler {
        public ToolDescriptor descriptor() { return new ToolDescriptor("github.searchCode", "Search repository code",
                ToolAction.SEARCH, ToolRisk.LOW, Set.of("github.read"), roles(), Set.of(), true, false, false); }
        public Object execute(Map<String,Object> args) { return Map.of("repository", REPOSITORY, "repositoryUrl", REPOSITORY_URL,
                "matches", 2, "files", java.util.List.of("backend/ai-security-gateway/src/main", "frontend/app/admin/page.tsx")); }
    }
    @Component
    public static class ReadFile implements ToolHandler {
        public ToolDescriptor descriptor() { return new ToolDescriptor("github.readFile", "Read a repository file",
                ToolAction.READ, ToolRisk.LOW, Set.of("github.read"), roles(), Set.of(), true, false, false); }
        public Object execute(Map<String,Object> args) { return Map.of("repository", REPOSITORY, "repositoryUrl", REPOSITORY_URL,
                "path", args.getOrDefault("path", "PR #382 diff"), "summary", "Mock review passed: tests and authorization checks present."); }
    }
    @Component
    public static class MergePullRequest implements ToolHandler {
        public ToolDescriptor descriptor() { return new ToolDescriptor("github.mergePullRequest", "Merge a pull request",
                ToolAction.WRITE, ToolRisk.HIGH, Set.of("github.write"), Set.of(UserRole.ADMIN), Set.of(), true, true, false); }
        public Object execute(Map<String,Object> args) { return Map.of("repository", REPOSITORY, "repositoryUrl", REPOSITORY_URL,
                "merged", true, "pullRequest", args.getOrDefault("pullRequest", 382), "mode", "deterministic-mock"); }
    }
}
