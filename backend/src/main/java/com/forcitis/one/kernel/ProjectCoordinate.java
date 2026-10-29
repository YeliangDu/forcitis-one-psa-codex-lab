package com.forcitis.one.kernel;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

@ValueObject
public record ProjectCoordinate(ProjectId project, @Nullable WbsNodeId wbsNode) {
    public ProjectCoordinate {
        Objects.requireNonNull(project, "project");
    }

    public static ProjectCoordinate ofProject(ProjectId project) {
        return new ProjectCoordinate(project, null);
    }

    public static ProjectCoordinate of(ProjectId project, WbsNodeId wbsNode) {
        return new ProjectCoordinate(project, Objects.requireNonNull(wbsNode, "wbsNode"));
    }

    public boolean isWholeProject() {
        return wbsNode == null;
    }
}
