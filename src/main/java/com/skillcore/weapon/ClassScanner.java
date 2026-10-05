package com.skillcore.weapon;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 包扫描工具 — 支持开发期（class 目录）与打包后（jar）两种形态。
 * 用于武器/盔甲技能注解自动注册。
 */
public final class ClassScanner {

    private ClassScanner() {
    }

    /**
     * 扫描指定包下的全部类。
     */
    public static List<Class<?>> scan(Plugin plugin, String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        ClassLoader loader = plugin.getClass().getClassLoader();
        String path = packageName.replace('.', '/');
        try {
            Enumeration<URL> resources = loader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();
                if ("jar".equals(protocol)) {
                    classes.addAll(scanJar(plugin, resource, packageName));
                } else if ("file".equals(protocol)) {
                    classes.addAll(scanDirectory(packageName, new File(resource.getFile())));
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("扫描技能包失败 " + packageName + ": " + ex.getMessage());
        }
        return classes;
    }

    private static List<Class<?>> scanJar(Plugin plugin, URL jarUrl, String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        String jarPath = jarUrl.getPath();
        int bang = jarPath.indexOf('!');
        if (bang >= 0) {
            jarPath = jarPath.substring(0, bang);
        }
        if (jarPath.startsWith("file:")) {
            jarPath = jarPath.substring("file:".length());
        }
        String packagePath = packageName.replace('.', '/');
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(packagePath) && name.endsWith(".class")) {
                    loadClass(plugin, name.substring(0, name.length() - 6).replace('/', '.'), classes);
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("扫描技能 jar 失败: " + ex.getMessage());
        }
        return classes;
    }

    private static List<Class<?>> scanDirectory(String packageName, File directory) {
        List<Class<?>> classes = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files == null) {
            return classes;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(scanDirectory(packageName + "." + file.getName(), file));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().replace(".class", "");
                try {
                    classes.add(Class.forName(className, false, ClassScanner.class.getClassLoader()));
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    // 扫描期忽略无法加载的类
                }
            }
        }
        return classes;
    }

    private static void loadClass(Plugin plugin, String className, List<Class<?>> out) {
        try {
            out.add(Class.forName(className, false, plugin.getClass().getClassLoader()));
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // 扫描期忽略无法加载的类
        }
    }
}
