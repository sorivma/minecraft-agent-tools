package dev.minecraftagenttools.forge.gregtech;

import dev.minecraftagenttools.gregtech.contract.GtRead;
import java.util.*;

/** Public GTNH API profile, verified against GT5 5.09.54.183. No bundled mod classes. */
class GtRegistry {
    @SuppressWarnings("unchecked")
    Map<String, Object> maps() {
        return (Map<String, Object>)
                GtRead.field(GtRead.type("gregtech.api.recipe.RecipeMap"), "ALL_RECIPE_MAPS");
    }

    Object[] machines() {
        return (Object[]) GtRead.field(GtRead.type("gregtech.api.GregTechAPI"), "METATILEENTITIES");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> materials() {
        return (Map<String, Object>)
                GtRead.call(GtRead.type("gregtech.api.enums.Materials"), "getMaterialsMap");
    }

    Object[] prefixes() {
        return (Object[]) GtRead.call(GtRead.type("gregtech.api.enums.OrePrefixes"), "values");
    }

    Object tiers(String field) {
        return GtRead.field(GtRead.type("gregtech.api.enums.GTValues"), field);
    }
}
