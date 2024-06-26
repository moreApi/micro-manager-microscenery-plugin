//////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, June 2009
//
// COPYRIGHT:    University of California, San Francisco, 2009
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.dialogs;


import java.awt.Dimension;
import java.awt.Toolkit;
import mmcorej.Configuration;
import mmcorej.StrVector;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.PropertyTableData;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * Editor for presets.  This edits the properties contained in a group and let's
 * the user st re them under a given name.  For instance, the group "Channels" will
 * have presets "GFP", "Rhodamine", etc.., and this is where the properties for
 * "GFP" etc.. are edited.
 */
public final class PresetEditor extends ConfigDialog {

   private static final long serialVersionUID = 8281144157746745260L;

   /**
    * Constructs the GUI editor for the given preset.
    *
    * @param groupName Name of the group, for instance "Channels".
    * @param presetName Name of preset, for instance "GFP"
    * @param studio The always present Studio object
    * @param newItem True when the preset does not exist yet, false otherwise
    */
   public PresetEditor(String groupName, String presetName, Studio studio, boolean newItem) {
      super(groupName, presetName, studio, newItem);
      instructionsText_ = "Specify property values for this preset:";
      nameFieldLabelText_ = "Preset name:";
      initName_ = presetName_;
      title_ = "Preset editor for the \"" + groupName + "\" configuration group";
      showUnused_ = false;
      showFlagsPanelVisible_ = false;
      scrollPaneTop_ = 70;
      numColumns_ = 2;
      PropertyTableData.Builder ptdb = new PropertyTableData.Builder(studio);
      data_ = ptdb.groupName(groupName_).presetName(presetName_).propertyValueColumn(1)
            .propertyUsedColumn(2).groupOnly(true).allowChangingProperties(true)
            .allowChangesOnlyWhenUsed(true).isPixelSizeConfig(false).build();
      initializeData();
      data_.setColumnNames("Property Name", "Preset Value", "");
      data_.setShowReadOnly(true);
      initialize();
      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
              getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setBounds(100, 100, 420, 300);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);
      super.setMinimumSize(new Dimension(400, 250));
   }

   @Override
   public void okChosen() {
      String newName = nameField_.getText();
      if (writePreset(initName_, newName)) {
         this.dispose();
      }
   }

   /**
    * "Writes the preset to the core.
    *
    * @param initName original name of the preset
    * @param newName new name (can be same as original)
    * @return true on success, false otherwise
    */
   public boolean writePreset(String initName, String newName) {

      // Check to make sure a group name has been specified.
      if (newName.length() == 0) {
         showMessageDialog("Please enter a name for this preset.");
         return false;
      }

      // Avoid clashing names
      StrVector groups = core_.getAvailableConfigs(groupName_);
      for (int i = 0; i < groups.size(); i++) {
         if (groups.get(i).contentEquals(newName) && !newName.contentEquals(initName)) {
            showMessageDialog("A preset by this name already exists in the \"" + groupName_
                  + "\" group.\nPlease enter a different name.");
            return false;
         }
      }

      StrVector cfgs = core_.getAvailableConfigs(groupName_);
      try {
         // Check if duplicate presets would be created
         Configuration otherPreset;
         boolean same;
         for (int j = 0; j < cfgs.size(); j++) {
            same = true;
            if (newItem_ || ! cfgs.get(j).contentEquals(initName)) {
               otherPreset = core_.getConfigData(groupName_, cfgs.get(j));
               for (PropertyItem item : data_.getProperties()) {
                  if (item.confInclude) {
                     if (otherPreset.isPropertyIncluded(item.device, item.name)) {
                        if (!item.getValueInCoreFormat().contentEquals(
                              otherPreset.getSetting(item.device, item.name).getPropertyValue())) {
                           same = false;
                        }
                     }
                  }
               }
               if (same) {
                  showMessageDialog("This combination of properties is already found in the \""
                        + cfgs.get(j)
                        + "\" preset.\nPlease choose unique property values for your new preset.");
                  return false;
               }
            }

         }

      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      // Rename the preset if its name has changed
      if (!newItem_ && !initName.contentEquals(newName)) {
         try {
            core_.renameConfig(groupName_, initName, newName);
         } catch (Exception e1) {
            ReportingUtils.logError(e1);
         }
      }

      // Define the preset.   
      for (PropertyItem item : data_.getProperties()) {
         if (item.confInclude) {
            try {
               core_.defineConfig(groupName_, newName, item.device, item.name,
                     item.getValueInCoreFormat());
            } catch (Exception e) {
               ReportingUtils.logError(e);
            }
         }
      }

      ((MMStudio) studio_).setConfigChanged(true);
      return true;

   }
}
