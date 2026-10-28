from pathlib import Path
p=Path('client/src/com/cburch/logisim/proj/Project.java')
s=p.read_text();s=s.replace('public void doAction(Action act) {','public void doAction(Action act) {\n\t\tif (com.logisim.multiplayer.Collaboration.intercept(this, act)) return;')
s=s.replace('public void undoAction() {','public void undoAction() {\n\t\tif (com.logisim.multiplayer.Collaboration.interceptUndo(this)) return;')
s=s.replace('public void setLogisimFile(LogisimFile value) {','public void setLogisimFile(LogisimFile value) {\n\t\tcom.logisim.multiplayer.Collaboration.fileChanging(this);')
s=s.replace('\n\tpublic void repaintCanvas() {','\n\t/** Added for multiplayer: never retain a local undo stack across shared revisions. */\n\tpublic void clearCollaborationHistory() { undoLog.clear(); undoMods = 0; }\n\n\tpublic void repaintCanvas() {')
p.write_text(s)
p=Path('client/src/com/cburch/logisim/gui/menu/LogisimMenuBar.java');s=p.read_text().replace('add(help = new MenuHelp(this));','add(help = new MenuHelp(this));\n\t\tadd(com.logisim.multiplayer.Collaboration.menu(proj));');p.write_text(s)
