"""Separate existing-record readback PID; no send or mutation port."""
from mq_lab_cutover_entry import main

if __name__ == '__main__':
    main('recovery')
