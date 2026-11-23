
package com.kocmomatics;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod("kocmomatics")
public class Kocmomatics {

    public static final String MOD_ID = "kocmomatics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Kocmomatics() {
        LOGGER.info("KOCMOMATICS 1.0 loaded!");
    }
}
mkdir -p src/main/resources/META-INF
nano src/main/resources/META-INF/mods.toml
modLoader="javafml"
loaderVersion="[47,)"
license="All Rights Reserved"

[[mods]]
modId="kocmomatics"
version="1.0"
displayName="KOCMOMATICS"
description='''
KOCMOMATICS Control Mod
'''
cd ~/KOCMOMATICS
find src -type f
