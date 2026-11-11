// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtfi.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Artifact contract: a fixture-free ordinary jar and a complete production-identical replacement. */
public final class TestlabPackagingRegression {

    private TestlabPackagingRegression() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new AssertionError("Expected ordinary and testlab runtime jars");
        int compared = 0;
        try (JarFile normal = new JarFile(args[0]); JarFile replacement = new JarFile(args[1])) {
            Enumeration<JarEntry> entries = normal.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.equalsIgnoreCase("META-INF/MANIFEST.MF")) continue;
                if (name.startsWith("com/miaokatze/gtfi/testlab/") || name.contains("/ClientSmoke"))
                    throw new AssertionError("Active test fixture leaked into ordinary jar: " + name);
                JarEntry other = replacement.getJarEntry(name);
                if (other == null) throw new AssertionError("Replacement lost production entry: " + name);
                try (InputStream left = normal.getInputStream(entry);
                    InputStream right = replacement.getInputStream(other)) {
                    if (!Arrays.equals(read(left), read(right)))
                        throw new AssertionError("Replacement changed production bytes: " + name);
                }
                compared++;
            }
            for (String required : new String[] { "com/miaokatze/gtfi/testlab/TestlabDriver.class",
                "com/miaokatze/gtfi/testlab/TestRuntime.class", "com/miaokatze/gtfi/client/ClientSmoke.class" }) {
                if (replacement.getJarEntry(required) == null) throw new AssertionError("Missing fixture: " + required);
            }
        }
        if (compared == 0) throw new AssertionError("No production entries compared");
        System.out.println(
            "TestlabPackagingRegression: " + compared + " production entries identical; active fixtures isolated");
    }

    private static byte[] read(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        return output.toByteArray();
    }
}
