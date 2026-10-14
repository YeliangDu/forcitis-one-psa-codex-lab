package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProjectCoordinateTest {

    @Test
    void aProjectIsRequired() {
        assertThatThrownBy(() -> new ProjectCoordinate(null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProjectId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WbsNodeId(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addressesTheWholeProjectOrOneWbsNode() {
        ProjectCoordinate whole = ProjectCoordinate.ofProject(new ProjectId(7));
        ProjectCoordinate node = ProjectCoordinate.of(new ProjectId(7), new WbsNodeId(3));
        assertThat(whole.isWholeProject()).isTrue();
        assertThat(node.isWholeProject()).isFalse();
        assertThat(node.wbsNode()).isEqualTo(new WbsNodeId(3));
        assertThat(whole).isNotEqualTo(node);
    }

    @Test
    void hasValueEquality() {
        assertThat(ProjectCoordinate.of(new ProjectId(7), new WbsNodeId(3)))
                .isEqualTo(ProjectCoordinate.of(new ProjectId(7), new WbsNodeId(3)))
                .hasSameHashCodeAs(ProjectCoordinate.of(new ProjectId(7), new WbsNodeId(3)));
        assertThatThrownBy(() -> ProjectCoordinate.of(new ProjectId(7), null)).isInstanceOf(NullPointerException.class);
    }
}
