/**
 * A very simple Micro-Manager plugin, intended to be used as an example for
 * developers wishing to create their own, actually useful plugins. This one
 * demonstrates performing various common tasks, but does not do anything
 * really useful.
 *
 * <p>Copy this code to a location of your choice, change the name of the project
 * (and the classes), build the jar file and copy it to the mmplugins folder
 * in your Micro-Manager directory.
 *
 * <p>Once you have it loaded and running, you can attach the NetBean debugger
 * and use all of NetBean's functionality to debug your code.  If you make a
 * generally useful plugin, please do not hesitate to send a copy to
 * info@micro-manager.org for inclusion in the Micro-Manager source code
 * repository.
 *
 * <p>LICENSE:      This file is distributed under the BSD license.
 * License text is included with the source distribution.
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 *
 * @author Nico Stuurman, 2012
 * @copyright University of California
 */


package org.micromanager.plugins.example;

import microscenery.network.VolumeReceiver;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class Example implements SciJavaPlugin, MenuPlugin {
   private Studio studio_;
   private ExampleFrame frame_;

   /**
    * This method receives the Studio object, which is the gateway to the
    * Micro-Manager API. You should retain a reference to this object for the
    * lifetime of your plugin. This method should not do anything except for
    * store that reference, as Micro-Manager is still busy starting up at the
    * time that this is called.
    */
   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   /**
    * This method is called when your plugin is selected from the Plugins menu.
    * Typically at this time you should show a GUI (graphical user interface)
    * for your plugin.
    */
   @Override
   public void onPluginSelected() {
      if (frame_ == null) {
         // We have never before shown our GUI, so now we need to create it.
         frame_ = new ExampleFrame(studio_);
      }
      frame_.setVisible(true);
   }

   /**
    * This string is the sub-menu that the plugin will be displayed in, in the
    * Plugins menu.
    */
   @Override
   public String getSubMenu() {
      return "Developer Tools";
   }

   /**
    * The name of the plugin in the Plugins menu.
    */
   @Override
   public String getName() {
      return "Example plugin";
   }

   @Override
   public String getHelpText() {
      return "Help text to tell the user what the plugin does.";
   }

   @Override
   public String getVersion() {
      return "2.0";
   }

   @Override
   public String getCopyright() {
      return "University of California, 2012-2015";
   }
}
