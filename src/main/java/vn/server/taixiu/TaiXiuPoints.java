
package vn.server.taixiu;

import net.milkbowl.vault.economy.Economy;
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
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class TaiXiuPoints extends JavaPlugin implements Listener {

    private final Map<UUID, Bet> bets = new HashMap<>();
    private final Random random = new Random();

    private Economy economy;
    private BossBar bossBar;
    private int secondsLeft;
    private boolean bettingOpen = true;

    private record Bet(String choice, double amount) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe(
                    "Khong tim thay Vault Economy. Hay kiem tra Vault va EssentialsX Economy!"
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        secondsLeft = Math.max(1, getConfig().getInt("round-seconds", 120));

        bossBar = Bukkit.createBossBar(
                "Tai Xiu - Con " + secondsLeft + " giay",
                BarColor.BLUE,
                BarStyle.SOLID
        );
        bossBar.setVisible(true);

        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        startRounds();
        getLogger().info("TaiXiuPoints da bat voi Vault Economy.");
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (registration == null) {
            return false;
        }

        economy = registration.getProvider();
        return economy != null;
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

        player.sendMessage(ChatColor.GREEN + "So du hien tai: "
                + economy.format(economy.getBalance(player)));
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

        if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
            player.sendMessage(ChatColor.GOLD + "So du: "
                    + economy.format(economy.getBalance(player)));
            player.sendMessage(ChatColor.YELLOW + "Dat cuoc: "
                    + (bettingOpen ? "Dang mo" : "Da dong"));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(ChatColor.YELLOW
                    + "Dung: /taixiu <tai|xiu|info> [so-tien]");
            return true;
        }

        if (!bettingOpen) {
            player.sendMessage(ChatColor.RED
                    + "Vong nay da dong dat cuoc.");
            return true;
        }

        String choice = args[0].toLowerCase(Locale.ROOT);

        if (!choice.equals("tai") && !choice.equals("xiu")) {
            player.sendMessage(ChatColor.RED + "Hay chon tai hoac xiu.");
            return true;
        }

        double amount;

        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "So tien khong hop le.");
            return true;
        }

        double min = getConfig().getDouble("min-bet", 10);
        double max = getConfig().getDouble("max-bet", 500);

        if (!Double.isFinite(amount) || amount < min || amount > max) {
            player.sendMessage(ChatColor.RED + "Moi luot can tu "
                    + economy.format(min) + " den "
                    + economy.format(max) + ".");
            return true;
        }

        UUID id = player.getUniqueId();

        if (bets.containsKey(id)) {
            player.sendMessage(ChatColor.RED
                    + "Ban da dat cuoc trong vong nay.");
            return true;
        }

        if (economy.getBalance(player) < amount) {
            player.sendMessage(ChatColor.RED + "Ban khong du tien.");
            return true;
        }

        var withdrawal = economy.withdrawPlayer(player, amount);

        if (!withdrawal.transactionSuccess()) {
            player.sendMessage(ChatColor.RED
                    + "Khong the tru tien: " + withdrawal.errorMessage);
            return true;
        }

        bets.put(id, new Bet(choice, amount));

        player.sendMessage(ChatColor.GREEN + "Da dat "
                + economy.format(amount) + " vao "
                + choice.toUpperCase(Locale.ROOT) + ".");

        return true;
    }

    private void startRounds() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (secondsLeft > 0) {
                    bossBar.setTitle(
                            "Tai Xiu - Con " + secondsLeft + " giay"
                    );

                    bossBar.setProgress(Math.max(
                            0.0,
                            Math.min(
                                    1.0,
                                    secondsLeft / (double) Math.max(
                                            1,
                                            getConfig().getInt("round-seconds", 120)
                                    )
                            )
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

                Bukkit.broadcastMessage(ChatColor.GOLD + "=== TAI XIU ===");
                Bukkit.broadcastMessage(ChatColor.YELLOW
                        + "Xuc xac: " + dice1 + ", " + dice2 + ", " + dice3
                        + " | Tong: " + total);
                Bukkit.broadcastMessage(ChatColor.AQUA
                        + "Ket qua: " + result.toUpperCase(Locale.ROOT));

                for (Map.Entry<UUID, Bet> entry : bets.entrySet()) {
                    UUID id = entry.getKey();
                    Player player = Bukkit.getPlayer(id);
                    Bet bet = entry.getValue();

                    if (bet.choice().equals(result)) {
                        double reward = bet.amount() * 2.0;

                        // Vault can OfflinePlayer, khong nhan UUID truc tiep.
                        var deposit = economy.depositPlayer(
                                Bukkit.getOfflinePlayer(id),
                                reward
                        );

                        if (deposit.transactionSuccess()) {
                            if (player != null) {
                                player.sendMessage(ChatColor.GREEN
                                        + "Ban thang! Nhan tong "
                                        + economy.format(reward) + ".");
                            }
                        } else {
                            getLogger().severe(
                                    "Khong the tra thuong cho " + id
                                            + ": " + deposit.errorMessage
                            );

                            if (player != null) {
                                player.sendMessage(ChatColor.RED
                                        + "Tra thuong that bai. Hay bao admin.");
                            }
                        }
                    } else if (player != null) {
                        player.sendMessage(ChatColor.RED
                                + "Ban thua "
                                + economy.format(bet.amount()) + ".");
                    }
                }

                bets.clear();

                secondsLeft = Math.max(
                        1,
                        getConfig().getInt("round-seconds", 120)
                );

                bettingOpen = true;
                bossBar.setColor(BarColor.BLUE);
                bossBar.setTitle("Vong moi bat dau!");
                bossBar.setProgress(1.0);
            }
        }.runTaskTimer(this, 0L, 20L);
    }
}
