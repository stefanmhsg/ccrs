"""Audit timestamps describe emission, independently of the cycle anchor."""
from datetime import datetime
import logging
import unittest
from ccrs_langgraph.audit import log_ccrs_event


class AuditTests(unittest.TestCase):
    def test_utc_emission_time_preserves_payload_and_does_not_mutate_input(self):
        logger = logging.getLogger('audit-fixture')
        fields = {'cycle_timestamp':'2000-01-01T00:00:00Z', 'reason':'quoted "value"\nline'}
        original = dict(fields)
        with self.assertLogs(logger,level='INFO') as output:
            log_ccrs_event(logger,'fixture',fields)
        self.assertEqual(original,fields)
        line = output.output[0]
        self.assertIn('cycle_timestamp=2000-01-01T00:00:00Z',line)
        self.assertNotIn('\n',line)
        timestamp = datetime.fromisoformat(line.split('timestamp_utc=',1)[1])
        self.assertIsNotNone(timestamp.utcoffset())
