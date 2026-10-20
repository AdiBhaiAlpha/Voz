#!/usr/bin/env python3
"""Require actual JUnit cases, not just a zero exit status/NO-SOURCE task.
Never reads credentials, SDP, audio files or device identifiers.
"""
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

phase = sys.argv[1]
if phase == 'instrumented':
    reports = Path('app/build/outputs/androidTest-results/connected').rglob('*.xml')
    expected = {
        ('com.example.online.NativeVoicePeerIntegrationTest', 'twoNativePeersNegotiateAndExchangeAudioRtp'),
        ('com.example.telecom.OnlineRegistrationUiTest', 'newIdentityRequiresDisplayNameAndCredentials'),
    }
elif phase == 'jvm':
    reports = Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')
    expected = set()
else:
    raise SystemExit('Unknown test report phase')

cases = [case for report in reports for case in ET.parse(report).getroot().iter('testcase')]
failed = [case for case in cases if case.find('failure') is not None or case.find('error') is not None]
skipped = [case for case in cases if case.find('skipped') is not None]
passed = {(case.get('classname'), case.get('name')) for case in cases if case not in failed and case not in skipped}
missing = expected - passed
if not passed or failed or missing:
    print(f'::error title=Android test evidence missing::{phase}: cases={len(cases)}, failures={len(failed)}, skipped={len(skipped)}, missing required tests={sorted(missing)}')
    raise SystemExit(1)
print(f'::notice title=Verified Android test results::{phase}: {len(cases)} actual cases; {len(failed)} failures; {len(skipped)} skipped. Required native RTP/UI tests confirmed: {sorted(expected)}')
