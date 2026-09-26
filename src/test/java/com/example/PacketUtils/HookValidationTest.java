package com.example.PacketUtils;

import com.example.Packets.BufferMethods;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

public class HookValidationTest {
    private static List<PacketDef> definitions() throws Exception {
        List<PacketDef> result = new ArrayList<>();
        for (Method method : PacketDef.class.getDeclaredMethods()) {
            if (method.getReturnType() == PacketDef.class && method.getParameterCount() == 0) {
                result.add((PacketDef) method.invoke(null));
            }
        }
        assertEquals("Every supported packet must be checked", 39, result.size());
        return result;
    }

    @Test
    public void allPacketArgumentsMatchTheSender() throws Exception {
        Set<String> packetFields = new HashSet<>();
        for (PacketDef def : definitions()) {
            assertTrue("Duplicate packet field " + def.name, packetFields.add(def.name));
            List<String> parameters = PacketReflection.parametersFor(def.type);
            assertNotNull(def.type.toString(), parameters);
            assertEquals(def.type.toString(), def.writeData.length, def.writeMethods.length);
            for (int i = 0; i < def.writeData.length; i++) {
                assertTrue(def.type + ": " + def.writeData[i], parameters.contains(def.writeData[i]));
                assertTrue(def.type.toString(), def.writeMethods[i].length > 0);
                for (String transform : def.writeMethods[i]) {
                    assertTrue(transform, transform.matches("v|strn|strc|[ras] [0-9]+"));
                }
            }
        }
        assertEquals(List.of("orientation"), PacketReflection.parametersFor(PacketType.SET_HEADING));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadLabelsBeforeTouchingTheClient() {
        PacketDef def = PacketDef.getSetHeading();
        PacketReflection.sendPacket(new PacketDef(def.name, new String[]{"direction"},
                def.writeMethods, def.type), 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingArgumentsBeforeTouchingTheClient() {
        PacketReflection.sendPacket(PacketDef.getSetHeading());
    }

    public static class Buffer {
        public byte[] aj = new byte[16];
        public int ay;
    }

    @Test
    public void encodesTheFourByteOrdersUsedByTheClient() {
        // Expected bytes follow execution order in xy.cy/em/ez/iu, including their jumps.
        String[][] orders = {{"r 24", "r 16", "r 8", "v"}, {"v", "r 8", "r 16", "r 24"},
                {"r 8", "v", "r 24", "r 16"}, {"r 16", "r 24", "v", "r 8"}};
        byte[][] expected = {{0x12, 0x34, 0x56, 0x78}, {0x78, 0x56, 0x34, 0x12},
                {0x56, 0x78, 0x12, 0x34}, {0x34, 0x12, 0x78, 0x56}};
        for (int i = 0; i < orders.length; i++) {
            Buffer buffer = new Buffer();
            for (String transform : orders[i]) BufferMethods.writeValue(transform, 0x12345678, buffer);
            assertArrayEquals(expected[i], Arrays.copyOf(buffer.aj, 4));
            assertEquals(4, buffer.ay * Integer.parseInt(ObfuscatedNames.indexMultiplier));
        }
    }

    @Test
    public void hooksBindToTheVerifiedClientWithoutLoadingGameClasses() throws Exception {
        String path = System.getenv("PACKETUTILS_CLIENT_JAR");
        assumeNotNull(path);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(path)));
        StringBuilder hex = new StringBuilder();
        for (byte value : hash) hex.append(String.format("%02x", value & 255));
        assertEquals("Use the official injected-client 1.12.39",
                "25f42961c400bd9dfff1554402441c0ba6d1cffd011163cb9b0b4c42ae194f85", hex.toString());
        try (JarFile jar = new JarFile(path)) {
            ClassNode packets = read(jar, ObfuscatedNames.clientPacketClassName);
            for (PacketDef def : definitions()) field(packets, def.name, "L" + packets.name + ";");
            ClassNode writer = read(jar, ObfuscatedNames.packetWriterClassName);
            String node = ObfuscatedNames.packetBufferNodeClassName;
            method(writer, ObfuscatedNames.addNodeMethodName, "(L" + node + ";I)V");
            String isaac = writer.fields.stream().filter(f -> f.name.equals(ObfuscatedNames.isaacCipherFieldName))
                    .findFirst().orElseThrow(AssertionError::new).desc;
            method(read(jar, ObfuscatedNames.classContainingGetPacketBufferNodeName),
                    ObfuscatedNames.getPacketBufferNodeMethodName, "(L" + packets.name + ";" + isaac + "B)L" + node + ";");
            ClassNode bufferNode = read(jar, node);
            String bufferType = bufferNode.fields.stream().filter(f -> f.name.equals(ObfuscatedNames.packetBufferFieldName))
                    .findFirst().orElseThrow(AssertionError::new).desc;
            ClassNode buffer = read(jar, bufferType.substring(1, bufferType.length() - 1));
            while (buffer.fields.stream().noneMatch(f -> f.name.equals(ObfuscatedNames.bufferArrayField))) {
                buffer = read(jar, buffer.superName);
            }
            field(buffer, ObfuscatedNames.bufferArrayField, "[B");
            field(buffer, ObfuscatedNames.bufferOffsetField, "I");
            assertEquals(1, Integer.parseInt(ObfuscatedNames.offsetMultiplier) * Integer.parseInt(ObfuscatedNames.indexMultiplier));
            ClassNode client = read(jar, "client");
            field(client, ObfuscatedNames.packetWriterFieldName, "L" + writer.name + ";");
            field(client, ObfuscatedNames.clientMillisField, "J");
            field(read(jar, ObfuscatedNames.MouseHandler_lastPressedTimeMillisClass),
                    ObfuscatedNames.MouseHandler_lastPressedTimeMillisField, "J");
            method(read(jar, ObfuscatedNames.doActionClassName), ObfuscatedNames.doActionMethodName,
                    "(IIIIIILjava/lang/String;Ljava/lang/String;IIB)V");
            field(read(jar, "dh"), ObfuscatedNames.pathLengthFieldName, "I");
            field(read(jar, "ct"), ObfuscatedNames.skullIconField, "I");
            decoder(read(jar, "ct"), "getSkullIcon", ObfuscatedNames.skullIconMultiplier);
            decoder(read(jar, "dh"), "getAnimation", ObfuscatedNames.getAnimationMultiplier);
        }
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        assertNotNull("Missing class " + name, jar.getJarEntry(name + ".class"));
        ClassNode node = new ClassNode();
        try (var in = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            new ClassReader(in).accept(node, 0);
        }
        return node;
    }

    private static void field(ClassNode node, String name, String descriptor) {
        assertTrue(node.name + "." + name + descriptor,
                node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)));
    }

    private static void method(ClassNode node, String name, String descriptor) {
        assertTrue(node.name + "." + name + descriptor,
                node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)));
    }

    private static void decoder(ClassNode node, String name, int multiplier) {
        var getter = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals("()I"))
                .findFirst().orElseThrow(AssertionError::new);
        for (var insn : getter.instructions) {
            if (insn instanceof LdcInsnNode && Integer.valueOf(multiplier).equals(((LdcInsnNode) insn).cst)) return;
        }
        fail("Missing multiplier in " + node.name + "." + name);
    }
}
