// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtfi.core;

/** NORMAL is Thaumcraft 4's sticky warp. */
public enum WarpType {

    TEMP(2, 1),
    NORMAL(1, 5),
    PERM(0, 20);

    public final byte syncId;
    public final int experience;

    WarpType(int syncId, int experience) {
        this.syncId = (byte) syncId;
        this.experience = experience;
    }

    public WarpType targetType() {
        return this == TEMP ? NORMAL : PERM;
    }
}
