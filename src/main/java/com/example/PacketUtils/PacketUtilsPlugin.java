package com.example.PacketUtils;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;

import javax.inject.Inject;
import javax.swing.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Singleton
@PluginDescriptor(
        name = "Packet Utils",
        description = "Packet Utils for Plugins",
        enabledByDefault = true,
        tags = {"ethan"}
)
public class PacketUtilsPlugin extends Plugin {
    @Inject
    PacketUtilsConfig config;
    @Inject
    Client client;
    static Client staticClient;
    public static Method addNodeMethod;
    public static boolean usingClientAddNode = false;
    public static final int CLIENT_REV = 240;
    public static final String CLIENT_VERSION = "1.12.39";
    private static String loadedConfigName = "";
    @Inject
    private PluginManager pluginManager;

    @Provides
    public PacketUtilsConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(PacketUtilsConfig.class);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e) {
        if (config.debug()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "Packet Utils", e.toString(), null);
            System.out.println(e);
        }
    }


    @Override
    @SneakyThrows
    public void startUp() {
        staticClient = client;
        if (client.getRevision() != CLIENT_REV || !CLIENT_VERSION.equals(RuneLiteProperties.getVersion())) {
            SwingUtilities.invokeLater(() ->
            {
                JOptionPane.showMessageDialog(null, "PacketUtils hooks require RuneLite " + CLIENT_VERSION + " / game revision " + CLIENT_REV);
                try {
                    pluginManager.setPluginEnabled(this, false);
                    pluginManager.stopPlugin(this);
                } catch (PluginInstantiationException ignored) {
                }
            });
            return;
        }
        //setupNeverlog();
        int feature = Runtime.version().feature();
        if (feature != 11) {
            for (int i = 0; i < 10; i++) {
                log.error("ETHAN VANN PLUGINS LOADED ON JAVA != 11 THIS IS NOT SUPPORTED");
                log.error("DEVELOPERS SHOULD IGNORE BUG REPORTS CONTAINING THIS LINE UNTIL THIS ISSUE IS RESOLVED");
            }
        } else {
            log.info("Ethan Vann Plugins loaded on Java 11");
        }
        setupRuneliteUpdateHandling(RuneLiteProperties.getVersion());
        cleanup();
        SwingUtilities.invokeLater(() ->
        {
            for (Plugin plugin : pluginManager.getPlugins()) {
                if (plugin.getName().equals("EthanApiPlugin")) {
                    if (pluginManager.isPluginEnabled(plugin)) {
                        continue;
                    }
                    try {
                        pluginManager.setPluginEnabled(plugin, true);
                        pluginManager.startPlugin(plugin);
                    } catch (PluginInstantiationException e) {
                        //e.printStackTrace();
                    }
                }
            }
        });
    }

    @SneakyThrows
    public static void setupNeverlog() {
        staticClient.setIdleTimeout(42069);
        for (Field declaredField : staticClient.getClass().getDeclaredFields()) {
            if (declaredField.getType() == int.class && Modifier.isStatic(declaredField.getModifiers())) {
                declaredField.setAccessible(true);
                int value = declaredField.getInt(null);
                if (value != 42069) {
                    declaredField.setAccessible(false);
                    continue;
                }
                System.out.println("found idle ticks field: " + declaredField.getName());
                declaredField.setInt(null, Integer.MAX_VALUE);
                declaredField.setAccessible(false);
            }
        }
    }

    @SneakyThrows
    public void cleanup() {
        if (!loadedConfigName.equals(makeString())) {
            for (int i = 0; i < 10; i++) {
                log.error("ETHAN VANN PLUGINS LOADED WITH INCORRECT CONFIG DATA THIS IS NOT SUPPORTED");
                log.error("DEVELOPERS SHOULD IGNORE BUG REPORTS CONTAINING THIS LINE UNTIL THIS ISSUE IS RESOLVED");
            }
        } else {
            log.info("config loaded from correct path");
        }
        Path codeSource = RuneLite.RUNELITE_DIR.toPath().resolve("PacketUtils");
        List<Path> toDelete = new ArrayList<>();
        toDelete.add(codeSource.resolve("vanilla.jar"));
        toDelete.add(codeSource.resolve("patched.jar"));
        toDelete.add(codeSource.resolve("doAction.class"));
        toDelete.add(codeSource.resolve("decompiled.txt"));
        for (Path path : toDelete) {
            Files.deleteIfExists(path);
        }
    }

    @SneakyThrows
    public void setupRuneliteUpdateHandling(String version) {
        // These hooks are verified against 1.12.39. Resolve the writer directly;
        // old cached decompiler guesses must not override the current mappings.
        addNodeMethod = PacketReflection.getPacketWriterClass().getDeclaredMethod(
                ObfuscatedNames.addNodeMethodName, PacketReflection.getPacketBufferNodeClass(), int.class);
        usingClientAddNode = true;
        loadedConfigName = makeString();
        log.info("Using mapped PacketWriter.addNode: {}", addNodeMethod);
    }

    public static void downloadVanillaJar(Path vanillaOutputPath, URL rlConfigURL) throws IOException {
        BufferedReader configReader = new BufferedReader(new InputStreamReader(rlConfigURL.openConnection().getInputStream()));
        while (configReader.ready()) {
            String line = configReader.readLine();
            if (line == null) {
                continue;
            }
            if (line.contains("runelite.gamepack")) {
                URL clientURL = new URL(line.split("=")[1]);
                log.info("Downloading vanilla client from " + clientURL);
                try (InputStream clientStream = clientURL.openStream()) {
                    Files.copy(clientStream, vanillaOutputPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        configReader.close();
    }

    @Override
    public void shutDown() {
        log.info("Shutdown");
    }

    @Inject
    private void init() {
        if (config.alwaysOn() && client.getRevision() == CLIENT_REV
                && CLIENT_VERSION.equals(RuneLiteProperties.getVersion())) {
            SwingUtilities.invokeLater(() ->
            {
                try {
                    RuneLite.getInjector().getInstance(PluginManager.class).setPluginEnabled(this, true);
                    RuneLite.getInjector().getInstance(PluginManager.class).startPlugin(this);
                } catch (PluginInstantiationException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public String makeString() {
        return RuneLiteProperties.getVersion() + "-" + client.getRevision() + ".txt";
    }
}