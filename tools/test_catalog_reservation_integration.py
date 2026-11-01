"""Source/deployment/count fixtures only; not Android ownership or authorization."""
from pathlib import Path
import unittest
from run_host_checks import COMMANDS, check_kind, parse_count
from compile_scope import requires_compile
ROOT=Path(__file__).resolve().parents[1]
CMD=['bash','tools/test_catalog_reservation.sh']
LINE='CatalogReservation HOST assertions: 65 PASS; no auth/file/Android acceptance\n'

class ReservationIntegrationTest(unittest.TestCase):
    def test_one_production_instance_and_owner_token_cleanup(self):
        root=ROOT/'app/src/main/java/vn/fonesi/app/storage'
        gate=(root/'CatalogProcessGate.java').read_text()
        self.assertIn('private static final CatalogReservation RESERVATION = new CatalogReservation()',gate)
        self.assertIn('static CatalogReservation.Token tryAcquire()',gate)
        matches=[p.name for p in (ROOT/'app/src/main/java').rglob('*.java') if 'new CatalogReservation()' in p.read_text()]
        self.assertEqual(matches,['CatalogProcessGate.java'])
        reader=(root/'ExistingCatalogRead.java').read_text()
        self.assertIn('private final CatalogReservation.Token reservation',reader)
        self.assertIn('CatalogReservation.Token reservation = CatalogProcessGate.tryAcquire()',reader)
        self.assertIn('reservation.requireActive()',reader)
        self.assertIn('quarantine(reservation);',reader)
        self.assertNotIn('AtomicBoolean',reader)
        self.assertNotIn('RESERVED.set',reader)
        core=(ROOT/'core/src/main/java/vn/fonesi/apk/foundation/CatalogReservation.java').read_text()
        close=core[core.index('        @Override public void close()'):]
        self.assertLess(close.index('state == State.RELEASED'),close.index('identity()'))
        self.assertLess(close.index('compareAndSet(this, null)'),close.index('state = State.RELEASED'))
        self.assertNotIn('.set(',core)
        self.assertNotIn('force',gate)

    def test_required_host_and_changed_compile_paths(self):
        self.assertEqual(COMMANDS.count(CMD),1)
        self.assertEqual(COMMANDS.count(['python3','tools/test_catalog_reservation_integration.py']),1)
        self.assertEqual(check_kind(CMD),'synthetic_assertions')
        self.assertEqual(parse_count(CMD,LINE),{'kind':'synthetic_assertions','count':65})
        for path in ['core/src/main/java/vn/fonesi/apk/foundation/CatalogReservation.java',
                     'app/src/main/java/vn/fonesi/app/storage/CatalogProcessGate.java']:
            self.assertTrue(requires_compile([path],'push'))

    def test_invalid_evidence_does_not_become_host_pass(self):
        for text in ['',LINE.replace('65','0'),LINE*2,'LIMITED HOST\n'+LINE,
                     LINE.replace('no auth/file/Android acceptance','auth/file/Android PASS')]:
            with self.subTest(text=text),self.assertRaises(AssertionError): parse_count(CMD,text)

if __name__=='__main__': unittest.main(verbosity=2)
