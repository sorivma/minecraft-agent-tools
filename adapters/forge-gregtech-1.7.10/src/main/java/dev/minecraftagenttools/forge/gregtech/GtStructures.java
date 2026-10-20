package dev.minecraftagenttools.forge.gregtech;

import static dev.minecraftagenttools.protocol.data.Data.map;

import dev.minecraftagenttools.gregtech.contract.GtRead;
import java.lang.reflect.Array;
import java.util.*;

/** Reads the compiled StructureLib program. Never invokes check/place/build/hints. */
final class GtStructures {
    @SuppressWarnings("unchecked")
    static Object read(Object machine, String piece, int offset, int limit) {
        Object definition = GtRead.call(machine, "getStructureDefinition");
        Map<String, Object> structures =
                (Map<String, Object>) GtRead.call(definition, "getStructures");
        List<String> names = new ArrayList<String>(structures.keySet());
        Collections.sort(names);
        Map<String, Object> result =
                map(
                        "pieces",
                        names,
                        "definitionClass",
                        definition.getClass().getName(),
                        "coordinateSystem",
                        "StructureLib local A/B/C from piece origin; controller offsets and world-facing transform unavailable",
                        "completeBuildPlan",
                        false,
                        "dynamicRepetitionAndConditions",
                        "not inferred; see machine description");
        if (piece == null) return result;
        Object steps = structures.get(piece);
        if (steps == null) throw new IllegalArgumentException("Unknown structure piece");
        int length = Array.getLength(steps);
        if (length > 32768)
            throw new IllegalStateException("Structure program exceeds 32768-step read limit");
        long[] abc = new long[3];
        List<Object> cells = new ArrayList<Object>();
        int i = 0;
        for (; i < length; i++) {
            Object element = Array.get(steps, i);
            if (Boolean.TRUE.equals(GtRead.call(element, "isNavigating"))) {
                for (int axis = 0; axis < 3; axis++) {
                    String name = "ABC".substring(axis, axis + 1);
                    abc[axis] =
                            (Boolean.TRUE.equals(GtRead.call(element, "reset" + name))
                                            ? 0
                                            : abc[axis])
                                    + ((Number) GtRead.call(element, "getStep" + name)).intValue();
                }
            } else {
                if (i >= offset)
                    cells.add(
                            map(
                                    "stepIndex",
                                    i,
                                    "a",
                                    abc[0],
                                    "b",
                                    abc[1],
                                    "c",
                                    abc[2],
                                    "elementClass",
                                    element.getClass().getName(),
                                    "requirements",
                                    GtData.safe(
                                            () ->
                                                    GtData.value(
                                                            GtRead.call(
                                                                    element,
                                                                    "getDescription",
                                                                    new Class<?>[] {Object.class},
                                                                    machine),
                                                            0))));
                abc[0]++;
                if (cells.size() >= limit) {
                    i++;
                    break;
                }
            }
        }
        result.putAll(
                map(
                        "piece",
                        piece,
                        "cells",
                        cells,
                        "programSteps",
                        length,
                        "nextOffset",
                        i < length ? i : null));
        return result;
    }
}
