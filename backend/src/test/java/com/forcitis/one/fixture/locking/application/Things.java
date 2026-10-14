package com.forcitis.one.fixture.locking.application;

import org.springframework.transaction.annotation.Transactional;

/** Deliberately wrong and right read use cases for ReadPathsDoNotLockTest. */
public final class Things {

    private Things() {
    }

    public interface ThingRepository {
        Object find(long id);

        Object findForUpdate(long id);
    }

    public static class Looker {
        private final ThingRepository things;

        public Looker(ThingRepository things) {
            this.things = things;
        }

        public Object lookAndLock(long id) {
            return things.findForUpdate(id);
        }
    }

    public static class GetThing {
        private final ThingRepository things;

        public GetThing(ThingRepository things) {
            this.things = things;
        }

        @Transactional
        public Object execute(long id) {
            return things.find(id);
        }
    }

    public static class ListThings {
        private final Looker looker;

        public ListThings(Looker looker) {
            this.looker = looker;
        }

        @Transactional(readOnly = true)
        public Object execute(long id) {
            return looker.lookAndLock(id);
        }
    }

    public static class CheckThing {
        private final ThingRepository things;

        public CheckThing(ThingRepository things) {
            this.things = things;
        }

        @Transactional(readOnly = true)
        public Object execute(long id) {
            return things.find(id);
        }
    }
}
