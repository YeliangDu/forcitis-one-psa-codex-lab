"""The checks of the pull request itself at `mq.py enqueue`, one entry for mq.py: each check exits with nothing queued, or lets a pull request it cannot read through and says so on stderr.
The title and the acceptance guard stay where they were (mq_title, mq_acceptance); this module holds the checks added since (#1561 the first-draft check, #1562 records, rules and new check files)."""
import draft_check
import mq_records


def enforce(root, gh, numbers, queued=()):
    """`queued`: the pull requests already in the queue (the records and rules checks count them)."""
    draft_check.enforce(root, gh, numbers)
    mq_records.enforce(root, gh, numbers, queued)
