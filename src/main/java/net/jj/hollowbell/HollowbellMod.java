package net.jj.hollowbell;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HollowbellMod implements ModInitializer {
    public static final String MOD_ID = "hollowbell";
    public static final Logger LOG = LoggerFactory.getLogger("Hollowbell");

    @Override
    public void onInitialize() {
        LOG.info("Hollowbell loaded");
    }
}
