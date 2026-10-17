package com.miaokatze.gtfi.core;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Checks bytecode without loading Minecraft classes; dev JAR compilation cannot detect side stripping. */
public final class DedicatedServerAbiRegression {

    private DedicatedServerAbiRegression() {}

    public static void main(String[] args) throws IOException {
        Path classes = Paths.get(args.length == 0 ? "build/classes/java/main" : args[0]);
        if (!Files.isDirectory(classes)) throw new AssertionError("Missing compiled classes: " + classes);
        List<Path> files = new ArrayList<Path>();
        try (Stream<Path> paths = Files.walk(classes.resolve("com/miaokatze/gtfi"))) {
            paths.filter(
                path -> path.toString()
                    .endsWith(".class"))
                .forEach(files::add);
        }
        int inspected = 0;
        for (Path file : files) {
            String relative = classes.relativize(file)
                .toString()
                .replace('\\', '/');
            if (relative.contains("/client/") || relative.endsWith("/ClientProxy.class")) continue;
            verify(Files.readAllBytes(file), relative);
            inspected++;
        }
        if (inspected == 0) throw new AssertionError("No common/server classes inspected");
        System.out.println("DedicatedServerAbiRegression: " + inspected + " common/server classes passed");
    }

    private static void verify(byte[] bytes, String label) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        if (input.readInt() != 0xCAFEBABE) throw new AssertionError("Invalid class: " + label);
        input.readUnsignedShort();
        input.readUnsignedShort();
        int count = input.readUnsignedShort();
        int[] tags = new int[count];
        Object[] pool = new Object[count];
        for (int index = 1; index < count; index++) {
            int tag = input.readUnsignedByte();
            tags[index] = tag;
            switch (tag) {
                case 1:
                    pool[index] = input.readUTF();
                    break;
                case 3:
                case 4:
                    input.readInt();
                    break;
                case 5:
                case 6:
                    input.readLong();
                    index++;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    pool[index] = input.readUnsignedShort();
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    pool[index] = new int[] { input.readUnsignedShort(), input.readUnsignedShort() };
                    break;
                case 15:
                    input.readUnsignedByte();
                    input.readUnsignedShort();
                    break;
                default:
                    throw new AssertionError("Unexpected constant pool tag " + tag + " in " + label);
            }
        }
        for (int index = 1; index < count; index++) {
            if (tags[index] == 7) {
                String owner = (String) pool[(Integer) pool[index]];
                if (owner.startsWith("net/minecraft/client/")) {
                    throw new AssertionError(label + " references client class " + owner);
                }
            }
            if (tags[index] != 10 && tags[index] != 11) continue;
            int[] reference = (int[]) pool[index];
            String owner = (String) pool[(Integer) pool[reference[0]]];
            int[] nameAndType = (int[]) pool[reference[1]];
            String name = (String) pool[nameAndType[0]];
            // These inherited EntityPlayer methods exist in the development merged JAR, but are
            // @SideOnly(CLIENT) and removed on a real dedicated server. Check MCP and SRG forms.
            if (name.equals("getItemInUse") || name.equals("getItemInUseCount")
                || name.equals("getItemInUseDuration")
                || name.equals("func_71011_bu")
                || name.equals("func_71052_bv")
                || name.equals("func_71057_bx")) {
                throw new AssertionError(label + " invokes stripped client API " + owner + "." + name);
            }
        }
    }
}
