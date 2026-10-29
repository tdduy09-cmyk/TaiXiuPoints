
package vn.server.taixiu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class TaiXiuPoints extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Bet> bets = new HashMap<>();
    private final Random random = new Random();

    private BossBar bossBar;
    private int secondsLeft;
    private boolean bettingOpen = true;

    private record Bet(String choice, int amount) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        secondsLeft = getConfig().getInt("round-seconds", 30);

        bossBar = Bukkit.createBossBar(
                "Tài Xỉu - Còn " + secondsLeft + " giây",
                BarColor.BLUE,
                BarStyle.SOLID
        );
        bossBar.setVisible(true);

        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
            points.putIfAbsent(
                    player.getUniqueId(),
                    getConfig().getInt("starting-points", 1000)
            );
        }

        startRounds();
        getLogger().info("TaiXiuPoints da bat!");
    }

    @Override
    public void onDisable() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        bossBar.addPlayer(player);
        points.putIfAbsent(
                player.getUniqueId(),
                getConfig().getInt("starting-points", 1000)
        );
        player.sendMessage(ChatColor.GREEN
                + "Ban co " + points.get(player.getUniqueId()) + " diem.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("taixiu")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lenh nay chi danh cho nguoi choi.");
            return true;
        }

        UUID id = player.getUniqueId();
        points.putIfAbsent(
                id,
                getConfig().getInt("starting-points", 1000)
        );

        if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
            player.sendMessage(ChatColor.GOLD + "Diem cua ban: "
                    + points.get(id));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(ChatColor.YELLOW
                    + "Dung: /taixiu <tai|xiu|info> [so-diem]");
            return true;
        }

        if (!bettingOpen) {
            player.sendMessage(ChatColor.RED
                    + "Vong nay da dong dat diem.");
            return true;
        }

        String choice = args[0].toLowerCase();
        if (!choice.equals("tai") && !choice.equals("xiu")) {
            player.sendMessage(ChatColor.RED
                    + "Hay chon tai hoac xiu.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED
                    + "So diem khong hop le.");
            return true;
        }

        int min = getConfig().getInt("min-bet", 10);
        int max = getConfig().getInt("max-bet", 500);

        if (amount < min || amount > max) {
            player.sendMessage(ChatColor.RED
                    + "Moi luot can tu " + min + " den " + max + " diem.");
            return true;
        }

        if (bets.containsKey(id)) {
            player.sendMessage(ChatColor.RED
                    + "Ban da dat diem trong vong nay.");
            return true;
        }

        if (points.get(id) < amount) {
            player.sendMessage(ChatColor.RED + "Ban khong du diem.");
            return true;
        }

        points.put(id, points.get(id) - amount);
        bets.put(id, new Bet(choice, amount));

        player.sendMessage(ChatColor.GREEN + "Da dat " + amount
                + " diem vao " + choice.toUpperCase() + ".");
        return true;
    }

    private void startRounds() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (secondsLeft > 0) {
                    bossBar.setTitle("Tài Xỉu - Còn " + secondsLeft + " giây");
                    bossBar.setProgress(Math.max(
                            0.0,
                            Math.min(1.0, secondsLeft
                                    / (double) Math.max(
                                    1,
                                    getConfig().getInt("round-seconds", 30)
                            ))
                    ));
                    secondsLeft--;
                    return;
                }

                bettingOpen = false;
                int dice1 = random.nextInt(6) + 1;
                int dice2 = random.nextInt(6) + 1;
                int dice3 = random.nextInt(6) + 1;
                int total = dice1 + dice2 + dice3;
                String result = total >= 11 ? "tai" : "xiu";

                Bukkit.broadcastMessage(ChatColor.GOLD
                        + "=== TAI XIU ===");
                Bukkit.broadcastMessage(ChatColor.YELLOW
                        + "Xuc xac: " + dice1 + ", " + dice2 + ", " + dice3
                        + " | Tong: " + total);
                Bukkit.broadcastMessage(ChatColor.AQUA
                        + "Ket qua: " + result.toUpperCase());

                for (Map.Entry<UUID, Bet> entry : bets.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    Bet bet = entry.getValue();

                    if (bet.choice().equals(result)) {
                        int reward = bet.amount() * 2;
                        points.merge(entry.getKey(), reward, Integer::sum);

                        if (player != null) {
                            player.sendMessage(ChatColor.GREEN
                                    + "Ban thang! Nhan lai " + reward
                                    + " diem.");
                        }
                    } else if (player != null) {
                        player.sendMessage(ChatColor.RED
                                + "Ban thua " + bet.amount() + " diem.");
                    }
                }

                bets.clear();
                secondsLeft = getConfig().getInt("round-seconds", 30);
                bettingOpen = true;
                bossBar.setColor(BarColor.BLUE);
                bossBar.setTitle("Vong moi bat dau!");
                bossBar.setProgress(1.0);
            }
        }.runTaskTimer(this, 0L, 20L);
    }
}
