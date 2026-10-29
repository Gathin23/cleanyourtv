from pathlib import Path
import subprocess
import tempfile
import unittest
import shutil

ROOT=Path(__file__).resolve().parent
COMPONENT='example.app/example.app.Player'
VIDEO='SurfaceView['+COMPONENT+'](BLAST)#0'
MAIN=COMPONENT+'#0'

@unittest.skipUnless(shutil.which('awk'), 'AWK fixture tests need a host awk executable')
class LayerTest(unittest.TestCase):
    def select(self, live, records):
        with tempfile.TemporaryDirectory() as d:
            live_file=Path(d)/'live';stats_file=Path(d)/'stats'
            live_file.write_text('\n'.join(live)+'\n')
            stats_file.write_text('totalFrames = 999999\ndisplayRefreshRate = 60 fps\n'+''.join(
                f'layerName = {name}\ntotalFrames = {count}\n' for name,count in records))
            return subprocess.check_output(['awk','-v','component='+COMPONENT,'-f',
                str(ROOT/'helper/select-layer.awk'),str(live_file),str(stats_file)],text=True).strip()
    def test_video_wins_over_ui(self):
        self.assertEqual(self.select([VIDEO,MAIN],[(VIDEO,100),(MAIN,400)]), VIDEO+'\t100')
    def test_old_removed_video_is_ignored(self):
        self.assertEqual(self.select([],[(VIDEO,100)]),'')
    def test_video_without_counters_does_not_fall_back_to_ui(self):
        self.assertEqual(self.select([VIDEO,MAIN],[(MAIN,400)]),'')
    def test_ambiguous_video_is_hidden(self):
        other=VIDEO.replace('#0','#1')
        self.assertEqual(self.select([VIDEO,other],[(VIDEO,100),(other,200)]),'')
    def test_prefixed_main_window_is_supported(self):
        self.assertEqual(self.select(['abc123 '+MAIN],[(MAIN,42)]),MAIN+'\t42')
    def test_other_activity_and_global_counts_are_ignored(self):
        self.assertEqual(self.select(['other.app/Player#0'],[('other.app/Player#0',100)]),'')
    def test_duplicate_records_are_ambiguous(self):
        self.assertEqual(self.select([VIDEO],[(VIDEO,100),(VIDEO,200)]),'')

if __name__=='__main__':unittest.main()
