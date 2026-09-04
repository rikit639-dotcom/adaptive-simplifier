/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2026 JOSM Plugin Builder
 */
package org.openstreetmap.josm.plugins.adaptivesimplifier;
import javax.swing.JMenu;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.adaptivesimplifier.actions.SimplifyAction;
import static org.openstreetmap.josm.tools.I18n.tr;

public class AdaptiveSimplifierPlugin extends Plugin {
    public AdaptiveSimplifierPlugin(PluginInformation info) {
        super(info);
        
        MainMenu menuBar = MainApplication.getMenu();
        JMenu myToolsMenu = null;
        String menuTitle = tr("Mytools");
        
        if (menuBar != null) {
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu m = menuBar.getMenu(i);
                if (m != null && menuTitle.equals(m.getText())) {
                    myToolsMenu = m;
                    break;
                }
            }
            
            if (myToolsMenu == null) {
                myToolsMenu = new JMenu(menuTitle);
                menuBar.addMenu(myToolsMenu, "mytools", 0, 5, null);
            }
            
            if (myToolsMenu.getMenuComponentCount() > 0) {
                myToolsMenu.addSeparator();
            }
            myToolsMenu.add(new SimplifyAction());
        }
    }
}