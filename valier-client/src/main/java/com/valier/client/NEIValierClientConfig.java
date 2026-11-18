package com.valier.client;

import codechicken.nei.api.IConfigureNEI;

/**
 * NEI plugin (NEI finds it by its NEI...Config name and runs it at its first world load, after it has set up its
 * search): makes NEI's "@" search find the Valier XP Crafter under Valier Tweaks ({@link ModSearch}). Players'
 * computers only; NEI never loads plugins on a server.
 */
public class NEIValierClientConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        ModSearch.install();
    }

    @Override
    public String getName() {
        return "Valier Client";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
