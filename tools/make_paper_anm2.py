"""Writes mod/schoolbag/resources/gfx/ui/schoolbag_paper.anm2.

An animation file of our own that points at the game's paper strip, the one
under the ink in its banners (gfx/ui/effect_cursepaper.png, loaded from the
player's copy of the game), so the mod can draw a longer strip for a long line
without shipping any art.

The strip is 168 x 35. Its torn ends are the first and last EDGE columns; the
middle between them is drawn stretched to whatever length the line needs.
"Left", "Middle" and "Right" each show one of those three parts, with its
top-left corner at the position it's drawn at, plus the 12 points the game's
banner puts between the ink's centre and the paper's top edge.
"""
import os
OUT = os.path.join(os.path.dirname(__file__), '..', 'mod', 'schoolbag', 'resources', 'gfx', 'ui', 'schoolbag_paper.anm2')
W, H, EDGE, DROP = 168, 35, 30, 12

def animation(name, x, width):
    frame = ('<Frame XPosition="0" YPosition="0" XPivot="0" YPivot="%d" XCrop="%d" YCrop="0" Width="%d" Height="%d" '
             'XScale="100" YScale="100" Delay="1" Visible="true" RedTint="255" GreenTint="255" BlueTint="255" '
             'AlphaTint="255" RedOffset="0" GreenOffset="0" BlueOffset="0" Rotation="0" Interpolated="false"/>'
             % (-DROP, x, width, H))
    root = ('<RootAnimation><Frame XPosition="0" YPosition="0" XScale="100" YScale="100" Delay="1" Visible="true" '
            'RedTint="255" GreenTint="255" BlueTint="255" AlphaTint="255" RedOffset="0" GreenOffset="0" '
            'BlueOffset="0" Rotation="0" Interpolated="false"/></RootAnimation>')
    return ('<Animation Name="%s" FrameNum="1" Loop="false">%s<LayerAnimations>'
            '<LayerAnimation LayerId="0" Visible="true">%s</LayerAnimation></LayerAnimations>'
            '<NullAnimations/><Triggers/></Animation>' % (name, root, frame))

xml = ('<AnimatedActor>\n'
       '<Info CreatedBy="Schoolbag" CreatedOn="26.11.2026 00:00:00" Version="1" Fps="30"/>\n'
       '<Content><Spritesheets><Spritesheet Path="Effect_CursePaper.png" Id="0"/></Spritesheets>'
       '<Layers><Layer Name="paper" Id="0" SpritesheetId="0"/></Layers><Nulls/><Events/></Content>\n'
       '<Animations DefaultAnimation="Middle">\n'
       + animation('Left', 0, EDGE) + '\n'
       + animation('Middle', EDGE, W - 2 * EDGE) + '\n'
       + animation('Right', W - EDGE, EDGE) + '\n'
       '</Animations>\n</AnimatedActor>\n')
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'w').write(xml)
print(OUT, len(xml))
