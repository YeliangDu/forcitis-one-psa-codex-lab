"""The checks of the pull request itself at `mq.py enqueue`, one entry for mq.py: each check exits with nothing queued, or lets a pull request it cannot read through and says so on stderr.
The title and the acceptance guard stay where they were (mq_title, mq_acceptance); this module holds the checks added since (#1561 the first-draft check)."""
import draft_check


def enforce(root, gh, numbers):
    draft_check.enforce(root, gh, numbers)
