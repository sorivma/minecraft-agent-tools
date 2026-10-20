package dev.minecraftagenttools.forge.schematica;

import static org.junit.Assert.*;

import org.junit.Test;

public class GregTechTilesTest {
    public interface Base {}

    public static class Machine implements Base {
        final Meta meta = new Meta();
        boolean valid;
        int ticks;

        public Meta getMetaTileEntity() {
            return meta;
        }

        public void setMetaTileEntity(Meta value) {
            valid = value != null && value.getBaseMetaTileEntity() == this;
        }

        public boolean isStillValid() {
            return valid;
        }

        public void updateEntity() {
            ticks++;
            throw new AssertionError("Ghost machines must not tick");
        }
    }

    public static class Meta {
        Base base;

        public Base getBaseMetaTileEntity() {
            return base;
        }

        public void setBaseMetaTileEntity(Base value) {
            base = value;
            ((Machine) value).setMetaTileEntity(this);
        }
    }

    @Test
    public void detachedMachineIsBoundAndItsCachedValidityIsRefreshedWithoutTicking()
            throws Exception {
        Machine machine = new Machine();
        assertFalse(machine.isStillValid());
        GregTechTiles.bind(machine);
        assertSame(machine, machine.meta.getBaseMetaTileEntity());
        assertTrue(
                "GT renders ERROR_RENDERING when this cached flag is false",
                machine.isStillValid());
        assertEquals(0, machine.ticks);
    }

    @Test
    public void existingBackReferenceDoesNotSkipRefreshingCachedValidity() throws Exception {
        Machine machine = new Machine();
        machine.meta.base = machine;
        GregTechTiles.bind(machine);
        assertTrue(machine.isStillValid());
        GregTechTiles.bind(machine);
        assertTrue(machine.isStillValid());
        assertEquals(0, machine.ticks);
    }
}
