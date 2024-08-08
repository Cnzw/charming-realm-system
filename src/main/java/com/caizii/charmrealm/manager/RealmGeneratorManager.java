package com.caizii.charmrealm.manager;

import com.caizii.charmrealm.CharmRealm;
import com.caizii.charmrealm.events.RealmFinishCreateEvent;
import com.caizii.charmrealm.task.RealmCreateTask;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class RealmGeneratorManager {

    private LinkedHashMap<UUID, RealmCreateTask> realmCreateTasks = new LinkedHashMap<UUID, RealmCreateTask>();
    private ThreadPoolExecutor threadPoolExecutor;
    private int threadPoolSize;

    public RealmGeneratorManager(int threadPoolSize) {

        this.threadPoolSize = threadPoolSize;
        this.threadPoolExecutor = new ThreadPoolExecutor(3, threadPoolSize,
                0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }

    public void addRealmCreateTask(UUID playerId, RealmCreateTask task) {
        String string = MessageFormat.format("§8[§6CharmRealms§8] §8(§a+§8) §7添加任务 <§a{0}§7> 至领域创建管理器中, 创建者 <§a{1}§7>", task.getTaskUUID(), Bukkit.getOfflinePlayer(playerId).getName());
        realmCreateTasks.put(playerId, task);
        Bukkit.getConsoleSender().sendMessage(string);
        executeNextTask();
    }

    public boolean isPlayerAlreadyCreated(UUID playerId) {
        return realmCreateTasks.containsKey(playerId);
    }

    private void executeNextTask() {
        for (Map.Entry<UUID, RealmCreateTask> entry : realmCreateTasks.entrySet()) {
            UUID playerId = entry.getKey();
            RealmCreateTask task = entry.getValue();

            threadPoolExecutor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    String string = MessageFormat.format("§8[§6CharmRealms§8] §8(§c-§8) §7任务 <§a{0}§7> 执行失败! 原因 <§c{1}§7>", task.getTaskUUID(), e.getMessage());
                    Bukkit.getConsoleSender().sendMessage(string);
                } finally {

                    Bukkit.getScheduler().runTask(CharmRealm.getInstance(), () -> {
                        Bukkit.getPluginManager().callEvent(new RealmFinishCreateEvent(task));
                    });
                    realmCreateTasks.remove(playerId);
                    String string = MessageFormat.format("§8[§6CharmRealms§8] §8(§c-§8) §7清除任务创建者 <§a{0}§7> §7创建的任务, 原因 <§a{1}§7>", Bukkit.getOfflinePlayer(playerId).getName(), "已完成");
                    Bukkit.getConsoleSender().sendMessage(string);
                    executeNextTask();
                }
            });

            break;
        }
    }

    public void cleanAllTasks() {
        Bukkit.getConsoleSender().sendMessage("§8[§6CharmRealms§8] §8(§a!§8) §7清除所有任务中...");
        realmCreateTasks.clear();
        threadPoolExecutor.shutdownNow(); // Interrupt all running tasks
        shutdown();
        // Reinitialize the thread pool
        threadPoolExecutor = new ThreadPoolExecutor(3, threadPoolSize,
                0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }

    public void shutdown() {
        threadPoolExecutor.shutdown();
        try {
            if (!threadPoolExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPoolExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Bukkit.getConsoleSender().sendMessage("§8[§6CharmRealms§8] §8(§c!§8) §7清除所有任务超时,强制结束线程池!");
            threadPoolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


}