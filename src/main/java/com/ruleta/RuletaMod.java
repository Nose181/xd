package com.ruleta;

import com.ruleta.net.Net;
import net.minecraftforge.fml.common.Mod;

@Mod(RuletaMod.MODID)
public class RuletaMod {
    public static final String MODID = "ruleta";

    public RuletaMod() {
        Net.registrar();
    }
}
