package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import org.junit.jupiter.api.Test;

class ProjectCoordinateTest {

    @Test
    void aProjectIsRequired() {
        assertThatThrownBy(() -> new ProjectCoordinate(null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProjectId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WbsNodeId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void addressesTheWholeProjectOrOneWbsNode() {
        ProjectCoordinate whole = ProjectCoordinate.ofProject(new ProjectId(TestIds.uuid(7)));
        ProjectCoordinate node = ProjectCoordinate.of(new ProjectId(TestIds.uuid(7)), new WbsNodeId(TestIds.uuid(3)));
        assertThat(whole.isWholeProject()).isTrue();
        assertThat(node.isWholeProject()).isFalse();
        assertThat(node.wbsNode()).isEqualTo(new WbsNodeId(TestIds.uuid(3)));
        assertThat(whole).isNotEqualTo(node);
    }

    @Test
    void hasValueEquality() {
        assertThat(ProjectCoordinate.of(new ProjectId(TestIds.uuid(7)), new WbsNodeId(TestIds.uuid(3))))
                .isEqualTo(ProjectCoordinate.of(new ProjectId(TestIds.uuid(7)), new WbsNodeId(TestIds.uuid(3))))
                .hasSameHashCodeAs(ProjectCoordinate.of(new ProjectId(TestIds.uuid(7)), new WbsNodeId(TestIds.uuid(3))));
        assertThatThrownBy(() -> ProjectCoordinate.of(new ProjectId(TestIds.uuid(7)), null)).isInstanceOf(NullPointerException.class);
    }
}
