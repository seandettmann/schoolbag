"""Writes mod/schoolbag/resources/gfx/ui/schoolbag_bossbar.anm2.

An animation file of our own that points at the game's boss health bar image
(gfx/ui/ui_bosshealthbar.png, loaded from the player's copy of the game), so the
mod can draw the bar while the game's HUD is hidden without shipping any art.

The game's image has the red fill strip on top (rows 0-31: white, columns 20-129)
and the skull and empty bar below (rows 32-63). Drawn at the same spot, the
strip sits inside the bar. "Fill" frame k shows the first k of its 110 columns;
"FillHit" is the same, untinted, for the flash when the boss is hit.
"""
import os
OUT = os.path.join(os.path.dirname(__file__), '..', 'mod', 'schoolbag', 'resources', 'gfx', 'ui', 'schoolbag_bossbar.anm2')
PIVOT = (68, 15)   # middle of the visible bar
FILL_START, FILL_LEN = 20, 110
RED = (255, 40, 40)

def frame(crop_y, width, tint=(255, 255, 255)):
    return ('<Frame XPosition="0" YPosition="0" XPivot="%d" YPivot="%d" XCrop="0" YCrop="%d" Width="%d" Height="32" '
            'XScale="100" YScale="100" Delay="1" Visible="true" RedTint="%d" GreenTint="%d" BlueTint="%d" AlphaTint="255" '
            'RedOffset="0" GreenOffset="0" BlueOffset="0" Rotation="0" Interpolated="false"/>'
            % (PIVOT[0], PIVOT[1], crop_y, width, *tint))

ROOT = ('<RootAnimation><Frame XPosition="0" YPosition="0" XScale="100" YScale="100" Delay="%d" Visible="true" '
        'RedTint="255" GreenTint="255" BlueTint="255" AlphaTint="255" RedOffset="0" GreenOffset="0" BlueOffset="0" '
        'Rotation="0" Interpolated="false"/></RootAnimation>')

def animation(name, frames0, frames1):
    n = max(len(frames0), len(frames1))
    l0 = ('<LayerAnimation LayerId="0" Visible="true">%s</LayerAnimation>' % ''.join(frames0)) if frames0 else '<LayerAnimation LayerId="0" Visible="false"/>'
    l1 = ('<LayerAnimation LayerId="1" Visible="true">%s</LayerAnimation>' % ''.join(frames1)) if frames1 else '<LayerAnimation LayerId="1" Visible="false"/>'
    return ('<Animation Name="%s" FrameNum="%d" Loop="false">%s<LayerAnimations>%s%s</LayerAnimations>'
            '<NullAnimations/><Triggers/></Animation>' % (name, n, ROOT % n, l0, l1))

fills = [frame(0, FILL_START + k, RED) for k in range(FILL_LEN + 1)]
hits = [frame(0, FILL_START + k) for k in range(FILL_LEN + 1)]
xml = ('<AnimatedActor>\n'
       '<Info CreatedBy="Schoolbag" CreatedOn="25.11.2026 00:00:00" Version="1" Fps="30"/>\n'
       '<Content><Spritesheets><Spritesheet Path="ui_bosshealthbar.png" Id="0"/></Spritesheets>'
       '<Layers><Layer Name="bar" Id="0" SpritesheetId="0"/><Layer Name="fill" Id="1" SpritesheetId="0"/></Layers>'
       '<Nulls/><Events/></Content>\n'
       '<Animations DefaultAnimation="Back">\n'
       + animation('Back', [frame(32, 150)], []) + '\n'
       + animation('Fill', [], fills) + '\n'
       + animation('FillHit', [], hits) + '\n'
       '</Animations>\n</AnimatedActor>\n')
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'w').write(xml)
print(OUT, len(xml))
