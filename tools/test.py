"""Run executed assertions. GUI suite is opt-in and requires a real desktop."""
import pathlib,subprocess,os,sys
root=pathlib.Path(__file__).resolve().parent.parent;os.chdir(root)
cp=os.pathsep.join(['build/multiplayer-classes','client/libs/*','lib/*','client/src'])
classes=['FeasibilityProof','ModelTest','SimulationTest','ConnectionFailureTest','SavedServersTest','SavedConnectionTest','IntegrationTest','UndoTest','MigrationTest','DesktopTest']
if '--gui' in sys.argv:classes.append('GuiTest')
for name in classes:
 print('Running '+name,flush=True)
 subprocess.run(['java']+([] if name=='GuiTest' else ['-Djava.awt.headless=true'])+['-cp',cp,'com.logisim.multiplayer.'+name],check=True,timeout=120)
