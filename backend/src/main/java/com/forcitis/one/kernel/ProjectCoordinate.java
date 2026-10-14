package com.forcitis.one.kernel;

import java.util.Objects;

/**
 * Project / WBS coordinate (ADR-0016 decision 8): which project a fact belongs to, and optionally which node of that
 * project's work breakdown structure. A coordinate without a node addresses the project as a whole.
 */
@ValueObject
public record ProjectCoordinate(ProjectId project, WbsNodeId wbsNode) {

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
