package dev.minecraftagenttools.forge.gregtech;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class GtStructuresTest {
    @Test
    public void navigationAndPagingPreservePieceLocalCoordinatesWithoutCheckingOrPlacing() {
        Machine machine = new Machine();
        Map<?, ?> first = (Map<?, ?>) GtStructures.read(machine, "main", 0, 1);
        Map<?, ?> cell = (Map<?, ?>) ((List<?>) first.get("cells")).get(0);
        assertEquals(0L, cell.get("a"));
        assertEquals(0L, cell.get("b"));
        Map<?, ?> second =
                (Map<?, ?>)
                        GtStructures.read(machine, "main", (Integer) first.get("nextOffset"), 1);
        cell = (Map<?, ?>) ((List<?>) second.get("cells")).get(0);
        assertEquals(2L, cell.get("a"));
        assertEquals(1L, cell.get("b"));
        assertEquals(0L, cell.get("c"));
        assertNull(second.get("nextOffset"));
        assertEquals(false, second.get("completeBuildPlan"));
    }

    public static class Machine {
        public Definition getStructureDefinition() {
            return new Definition();
        }
    }

    public static class Definition {
        public Map<String, Element[]> getStructures() {
            return Collections.singletonMap(
                    "main",
                    new Element[] {
                        new Element() {},
                        new Element() {
                            public boolean isNavigating() {
                                return true;
                            }

                            public boolean resetA() {
                                return true;
                            }

                            public int getStepA() {
                                return 2;
                            }

                            public int getStepB() {
                                return 1;
                            }
                        },
                        new Element() {}
                    });
        }
    }

    public interface Element {
        default boolean isNavigating() {
            return false;
        }

        default int getStepA() {
            return 0;
        }

        default int getStepB() {
            return 0;
        }

        default int getStepC() {
            return 0;
        }

        default boolean resetA() {
            return false;
        }

        default boolean resetB() {
            return false;
        }

        default boolean resetC() {
            return false;
        }

        default List<String> getDescription(Object context) {
            return Arrays.asList("Input hatch", "Casing");
        }

        default boolean check() {
            throw new AssertionError("World check must not run");
        }

        default void placeBlock() {
            throw new AssertionError("World mutation must not run");
        }
    }
}
