package com.nearvanilla.iceCream;

import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.lightning.LightningModule;
import com.nearvanilla.iceCream.modules.modulesInfo.ModulesInfoModule;
import com.nearvanilla.iceCream.modules.muteDeaths.MuteDeathsModule;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import com.nearvanilla.iceCream.modules.staffMode.StaffModeModule;
import com.nearvanilla.iceCream.modules.wanderful.WanderfulModule;
import java.util.List;

/**
 * Mochi is a minified variant of the IceCream plugin, intended for smaller servers. It includes
 * only a subset of modules: lightning, muteDeaths, spectator, staffMode, and wanderful.
 *
 * @see IceCream
 */
public class Mochi extends IceCream {

  @Override
  protected List<Module> getModules() {
    return List.of(
        new LightningModule(),
        new ModulesInfoModule(),
        new MuteDeathsModule(),
        new SpectatorModule(),
        new StaffModeModule(),
        new WanderfulModule());
  }
}
