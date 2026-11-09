"""Test-only adapter: keep #55's frozen fault injection and assertions unchanged."""
from lab55_cleanup_fixture_frozen import OfflinePorts, git, main, snapshot


def release_health(self, merge_sha):
    tip = git(self.repository, "rev-parse", "refs/heads/main")
    return dict(action="healthy" if tip == merge_sha else "hold", head_sha=tip)


OfflinePorts.release_health = release_health

if __name__ == "__main__":
    main()
