package com.forcitis.one.fixture.doubles;

import com.forcitis.one.platform.integration.api.ArchiveVault;
import com.forcitis.one.platform.integration.api.ArchivedObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** An archive vault that keeps what it was given in memory; set failure to make the next archive and probe calls fail. */
public final class RecordingArchiveVault implements ArchiveVault {
    public final List<List<String>> batches = new CopyOnWriteArrayList<>();
    public volatile RuntimeException failure;
    public volatile int probes;
    public volatile RuntimeException retentionFailure;

    public void reset() {
        batches.clear();
        failure = null;
        probes = 0;
        retentionFailure = null;
    }

    public List<String> lines() {
        List<String> all = new ArrayList<>();
        batches.forEach(all::addAll);
        return all;
    }

    @Override
    public ArchivedObject archive(String kind, List<String> lines) {
        if (failure != null) {
            throw failure;
        }
        batches.add(List.copyOf(lines));
        return new ArchivedObject("test/" + kind + "/" + batches.size(), 1, "0".repeat(64));
    }

    @Override
    public void requireTotalKeepPeriod(String what, java.time.Period online) {
        if (retentionFailure != null) {
            throw retentionFailure;
        }
    }

    @Override
    public void probe(String kind) {
        probes++;
        if (failure != null) {
            throw failure;
        }
    }
}
