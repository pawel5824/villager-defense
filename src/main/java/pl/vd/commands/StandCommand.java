package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;
import pl.vd.stand.ArmorStandTuner;
import pl.vd.stand.ArmorStandTuner.DisplayPart;
import pl.vd.stand.StandPreset;
import pl.vd.util.AntiSpam;
import pl.vd.util.ItemBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class StandCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PARAMS = List.of(
            "head:", "hand:", "offhand:", "small:", "arms:", "visible:", "base:",
            "sink:", "yaw:", "pitch:",
            "hpitch:", "hyaw:", "hroll:",
            "bpitch:", "byaw:", "broll:",
            "rpitch:", "ryaw:", "rroll:",
            "lpitch:", "lyaw:", "lroll:",
            "rgpitch:", "rgyaw:", "rgroll:",
            "lgpitch:", "lgyaw:", "lgroll:");

    /** Znane podkomendy /stojak (pierwszy argument) - inny pierwszy argument może być nazwą stojaka. */
    private static final java.util.Set<String> KNOWN_SUBS = java.util.Set.of(
            "stworz", "help", "remove", "usun", "usunczesc", "removepart",
            "przywroc", "przywróć", "restore", "bind", "part", "partclear",
            "head", "hand", "offhand", "undo", "save", "saveitem", "offset",
            "place", "item", "list", "delete", "copy", "paste", "move", "tam",
            "scale", "rotate", "zamien", "zmien", "change", "glow", "aktywny",
            "nazwa", "name", "nazwij", "nadaj_nazwe", "nadaj", "visible", "niewidoczny", "niewidzialny", "snap",
            "sprzataj", "posprzataj", "czysc", "cleanup", "duplikuj", "duplikat",
            "duplicate", "stats", "statystyki", "informacja", "informacje", "odzyskaj", "recover", "znajdz", "find",
            "ponow", "redo", "przywroc_parts", "przywroc_czesci", "autosave", "lustro", "mirror",
            "krzeslo", "chair", "funkcja", "funkcje", "liczba", "count", "ile", "ilosc", "ilość",
            "odbicie", "odbij", "mirrorpart", "lustroczesc", "lustro_czesc", "dajitemy",
            "dekoracja", "dekoracje", "zegar");

    private final VillagerDefensePlugin plugin;

    /** Oczekująca prośba o usunięcie zapisu (do potwierdzenia słowem confirm). */
    private static class PendingDelete {
        final String name;
        final long expiresAt;

        PendingDelete(String name, long expiresAt) {
            this.name = name;
            this.expiresAt = expiresAt;
        }
    }

    /** Na ile ważna jest prośba o usunięcie (20 s - tyle czasu na confirm). */
    private static final long DELETE_CONFIRM_MS = 20_000;

    /** Oczekujące potwierdzenia usunięcia per gracz (UUID -> prośba). */
    private final Map<UUID, PendingDelete> pendingDeletes = new HashMap<>();

    public StandCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda działa tylko w grze."));
            return true;
        }
        if (!player.hasPermission("vd.admin")) {
            AntiSpam.send(player, "§cBrak uprawnień.");
            return true;
        }
        if (args.length == 0) {
            plugin.getArmorStandTuner().sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        // /stojak <nazwa> remove jest celowo NIEdostępne - usuwanie działa TYLKO na
        // stojaku, na który patrzysz (/stojak remove). Usuwanie po nazwie wymaga
        // podwójnego potwierdzenia: /stojak delete <nazwa> confirm.
        if (args.length >= 2 && !KNOWN_SUBS.contains(sub)
                && args[1].equalsIgnoreCase("remove")) {
            AntiSpam.send(player, "§cUsuwanie działa tylko na stojaku, na który patrzysz: §e/stojak remove§c. "
                    + "Po nazwie usuniesz zapis komendą: §e/stojak delete <nazwa> confirm§c.");
            return true;
        }
        // /stojak <nazwa stojaka> <operacja> ... - operacja na stojaku o nazwie (bez
        // patrzenia): znajdź stojak po nazwie, ustaw jako AKTYWNY i przetwarzaj resztę
        // normalnie. Składnia materiałowa /stojak <materiał> move|scale|rotate ma
        // pierwszeństwo (żeby nie przejmować jej, gdy stojak nazywa się jak materiał).
        if (args.length >= 2 && !KNOWN_SUBS.contains(sub)
                && !(args[1].equalsIgnoreCase("move") || args[1].equalsIgnoreCase("scale")
                || args[1].equalsIgnoreCase("rotate"))) {
            ArmorStand named = plugin.getArmorStandTuner().findNamedStand(player, sub);
            if (named != null) {
                plugin.getArmorStandTuner().bind(named);
                plugin.getArmorStandTuner().setActive(player, named);
                AntiSpam.sendActionBar(player, "§7Stojak o nazwie §e" + sub
                        + "§7 - edytujesz go bez patrzenia (§e/stojak aktywny§7 - pozycja).");
                args = java.util.Arrays.copyOfRange(args, 1, args.length);
                sub = args[0].toLowerCase();
            }
        }
        if (sub.equals("stworz")) {
            Map<String, String> params = new HashMap<>();
            int start = 1;
            // opcjonalne dokładne koordynaty stóp: /stojak stworz <x> <y> <z> [parametry...]
            Location exact = null;
            if (args.length >= 4) {
                Double x = parseDoubleOr(args[1], null);
                Double y = parseDoubleOr(args[2], null);
                Double z = parseDoubleOr(args[3], null);
                if (x != null && y != null && z != null) {
                    exact = new Location(player.getWorld(), x, y, z);
                    start = 4;
                }
            }
            for (int i = start; i < args.length; i++) {
                String arg = args[i];
                int idx = arg.indexOf(':');
                if (idx > 0) {
                    params.put(arg.substring(0, idx).toLowerCase(), arg.substring(idx + 1));
                } else {
                    params.put("head", arg);
                }
            }
            ArmorStand created = plugin.getArmorStandTuner().spawn(player, params, exact);
            plugin.getArmorStandTuner().setActive(player, created);
            if (exact != null) {
                AntiSpam.send(player, "§aPostawiono stojak do strojenia na koordynatach §e"
                        + fmt(exact.getX()) + " " + fmt(exact.getY()) + " " + fmt(exact.getZ()) + "§a.");
            } else {
                AntiSpam.send(player, "§aPostawiono stojak do strojenia. Kliknij go STICKiem, żeby zacząć (§e/stojak help§a).");
            }
            return true;
        }
        if (sub.equals("help")) {
            int page = 1;
            if (args.length >= 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    page = 1;
                }
            }
            plugin.getArmorStandTuner().sendHelp(player, page);
            return true;
        }
        if (sub.equals("remove")) {
            ArmorStand stand = plugin.getArmorStandTuner().findLookedStand(player, true);
            if (stand == null) {
                // fallback: usuń też stojak spoza sesji (np. postawiony przed restartem,
                // gdy status "strojony" zniknął z pamięci). Duchy-podglądy są pomijane.
                stand = plugin.getArmorStandTuner().findLookedStand(player, false);
            }
            if (stand == null) {
                // celowo TYLKO patrząc: bez tego łatwo usunąć nie ten stojak (np. aktywny)
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak. Usuwanie działa tylko na stojaku, na który patrzysz.");
                return true;
            }
            plugin.getArmorStandTuner().remove(stand, player);
            return true;
        }
        // Autosave (jeden plik nadpisywany co minutę): /stojak autosave - lista zapisanych
        // stojaków; /stojak autosave place <nazwa> - przywróć w oryginalne miejsce
        if (sub.equals("autosave")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("place")) {
                if (args.length < 3) {
                    AntiSpam.send(player, "§cUżyj: /stojak autosave place <nazwa>");
                    return true;
                }
                if (plugin.getStandPresetManager().restoreAutosave(player, args[2])) {
                    AntiSpam.send(player, "§aPrzywrócono stojak §e" + args[2] + "§a z autosave.");
                } else {
                    AntiSpam.send(player, "§cNie znaleziono §e" + args[2] + "§c w autosave (stojaki/autosave.yml).");
                }
                return true;
            }
            List<StandPreset> all = plugin.getStandPresetManager().readAutosave();
            if (all.isEmpty()) {
                AntiSpam.send(player, "§7Brak zapisu autosave (stojaki/autosave.yml). Zapisywany co minutę.");
                return true;
            }
            player.sendMessage(Component.text("§6=== /stojak autosave §7(" + all.size() + " stojaków) §6==="));
            for (StandPreset p : all) {
                String loc = p.world != null
                        ? p.world + " " + (int) p.locX + " " + (int) p.locY + " " + (int) p.locZ
                        : "(bez pozycji)";
                player.sendMessage(Component.text("§f" + p.name + " §7- " + p.parts.size()
                        + (p.parts.size() == 1 ? " część" : " części") + " @ §e" + loc
                        + " §7(§e/stojak autosave place " + p.name + "§7)"));
            }
            return true;
        }
        // Przywracanie przypadkowo usuniętego stojaka (razem z częściami): /stojak przywroc
        if (sub.equals("przywroc") || sub.equals("przywróć") || sub.equals("restore")) {
            if (plugin.getArmorStandTuner().restoreRemoved(player)) {
                AntiSpam.sendActionBar(player, "§aPrzywrócono usunięty stojak (z częściami, aktywny).");
            } else {
                AntiSpam.send(player, "§cBrak usuniętego stojaka do przywrócenia - najpierw usuń: /stojak remove albo SHIFT+BARRIER.");
            }
            return true;
        }
        if (sub.equals("usun") || sub.equals("usunczesc") || sub.equals("removepart")) {
            // /stojak usun all czesc <materiał> (albo /stojak usun all <materiał>) -
            // usuń WSZYSTKIE części z danego materiału na stojaku naraz
            if (args.length >= 2 && (args[1].equalsIgnoreCase("all")
                    || args[1].equalsIgnoreCase("wszystkie") || args[1].equalsIgnoreCase("wszystko"))) {
                int mi = 2;
                if (args.length >= 3 && (args[2].equalsIgnoreCase("czesc")
                        || args[2].equalsIgnoreCase("część") || args[2].equalsIgnoreCase("part"))) {
                    mi = 3;
                }
                if (args.length < mi + 1) {
                    AntiSpam.send(player, "§cUżyj: /stojak usun all czesc <materiał> (np. /stojak usun all czesc stone)");
                    return true;
                }
                Material mat = Material.matchMaterial(args[mi]);
                if (mat == null) {
                    AntiSpam.send(player, "§cNieznany materiał: " + args[mi]);
                    return true;
                }
                plugin.getArmorStandTuner().removePartsByMaterial(player, mat);
                return true;
            }
            // /stojak usun czesc <liczba> [materiał] - usuń część o numerze z action
            // bara (CZĘŚĆ 1, CZĘŚĆ 2, ... - przewijanie STICKiem); opcjonalnie tylko
            // część z danego materiału (np. usun czesc 5 stone)
            if (args.length >= 2 && (args[1].equalsIgnoreCase("czesc")
                    || args[1].equalsIgnoreCase("część") || args[1].equalsIgnoreCase("part"))) {
                Integer num = parseIntOr(args.length >= 3 ? args[2] : null, null);
                if (num == null || num < 1) {
                    AntiSpam.send(player, "§cUżyj: /stojak usun czesc <liczba> [materiał] (liczba = numer części z action bara)");
                    return true;
                }
                Material mat = null;
                if (args.length >= 4) {
                    mat = Material.matchMaterial(args[3]);
                    if (mat == null) {
                        AntiSpam.send(player, "§cNieznany materiał: " + args[3]);
                        return true;
                    }
                }
                plugin.getArmorStandTuner().removePartNumber(player, num, mat);
                return true;
            }
            // usuń doczepioną część (wizualny blok/przedmiot), na którą patrzysz
            if (args.length >= 2) {
                Material m = Material.matchMaterial(args[1]);
                if (m == null) {
                    AntiSpam.send(player, "§cNieznany materiał: " + args[1]);
                    return true;
                }
                plugin.getArmorStandTuner().removeLookedPart(player, m);
            } else {
                plugin.getArmorStandTuner().removeLookedPart(player);
            }
            return true;
        }
        // bind / odzyskaj: dołączenie stojaka do strojenia (też po restarcie serwera,
        // gdy stojak stoi w świecie, ale status edytowalny zniknął z pamięci)
        if (sub.equals("bind") || sub.equals("odzyskaj") || sub.equals("recover")) {
            ArmorStand stand = plugin.getArmorStandTuner().findLookedStand(player, false);
            if (stand == null) {
                stand = plugin.getArmorStandTuner().getActive(player);
                if (stand != null) {
                    AntiSpam.send(player, "§7Nie patrzysz na żaden stojak - dołączam AKTYWNY stojak.");
                }
            }
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            if (plugin.getArmorStandTuner().bind(stand)) {
                plugin.getArmorStandTuner().setActive(player, stand);
                AntiSpam.send(player, "§aStojak dołączony do strojenia (aktywny).");
            } else {
                AntiSpam.send(player, "§cNie udało się dołączyć stojaka.");
            }
            return true;
        }
        // dajitemy: itemy do edycji stojaków - patyk, kość, blaze rod, cukier,
        // redstone, proch i bariera (po kolei do ekwipunku, pierwszy = pierwszy wolny slot)
        if (sub.equals("dajitemy")) {
            ItemStack[] tools = {
                    new ItemBuilder(Material.STICK, 1).name("§6Zmiana części stojaka").build(),
                    new ItemBuilder(Material.BONE, 1).name("§6Obracanie części (dół)").build(),
                    new ItemBuilder(Material.BLAZE_ROD, 1).name("§6Obracanie części (góra)").build(),
                    new ItemBuilder(Material.SUGAR, 1).name("§6Przewijanie funkcji itemu/stojaka").build(),
                    new ItemBuilder(Material.REDSTONE, 1).name("§6Podświetlenie stojaka").build(),
                    new ItemBuilder(Material.GUNPOWDER, 1).name("§6Widoczność stojaka").build(),
                    new ItemBuilder(Material.BARRIER, 1).name("§6Usuwanie części/całości stojaka").build(),
            };
            Map<Integer, ItemStack> left = player.getInventory().addItem(tools);
            if (left.isEmpty()) {
                AntiSpam.send(player, "§aOtrzymałeś itemy do edycji stojaków (§7patrz /stojak help§a).");
            } else {
                AntiSpam.send(player, "§cEkwipunek pełny - nie zmieściło się " + left.size() + " "
                        + (left.size() == 1 ? "item" : "itemów") + ".");
            }
            return true;
        }
        // Znajdź stojaki pluginu w świecie (po restarcie, gdy nie wiadomo, gdzie stoją):
        // /stojak znajdz - lista od najbliższego: nazwa, koordynaty, liczba części
        if (sub.equals("znajdz") || sub.equals("find")) {
            List<org.bukkit.entity.ArmorStand> stands = plugin.getArmorStandTuner().findPluginStands(player);
            if (stands.isEmpty()) {
                AntiSpam.send(player, "§7Brak stojaków pluginu w tym świecie (oznaczonych tagiem albo z częściami).");
                return true;
            }
            StringBuilder sb = new StringBuilder("§6Stojaki w świecie (od najbliższego):");
            int shown = 0;
            for (org.bukkit.entity.ArmorStand s : stands) {
                if (shown++ >= 15) {
                    sb.append("\n§7... i " ).append(stands.size() - shown + 1).append(" więcej");
                    break;
                }
                Location l = s.getLocation();
                String name = s.getCustomName();
                String nm = (name != null && !name.isEmpty()) ? name
                        : (plugin.getArmorStandTuner().hasParts(s) ? "(z częściami)" : "(stojak)");
                sb.append("\n§f").append(nm).append(" §7@ §e")
                        .append(l.getWorld().getName()).append(" ")
                        .append((int) l.getX()).append(" ")
                        .append((int) l.getY()).append(" ")
                        .append((int) l.getZ())
                        .append(" §7(części: ").append(plugin.getArmorStandTuner().getParts(s).size()).append(")");
            }
            player.sendMessage(Component.text(sb.toString()));
            AntiSpam.send(player, "§7Odzyskaj i edytuj: patrz na stojak i kliknij go przedmiotem albo §e/stojak odzyskaj§7. Teleport: §e/tp <koordynaty>§7.");
            return true;
        }
        // Licznik encji (do oceny lagów): /stojak liczba [promień] - ile stojaków i części
        // pluginu stoi w promieniu (domyślnie 100 bloków) oraz ile jest WSZYSTKICH encji.
        // Tylko dla operatora (vd.admin - jak reszta komend).
        if (sub.equals("liczba") || sub.equals("count") || sub.equals("ile")
                || sub.equals("ilosc") || sub.equals("ilość")) {
            double radius = 100;
            if (args.length >= 2) {
                Double r = parseDoubleOr(args[1], null);
                if (r == null || r < 1 || r > 500) {
                    AntiSpam.send(player, "§cUżyj: /stojak liczba [promień 1-500] (domyślnie 100)");
                    return true;
                }
                radius = r;
            }
            ArmorStandTuner tuner = plugin.getArmorStandTuner();
            Location pl = player.getLocation();
            double r2 = radius * radius;
            int stands = 0, parts = 0, total = 0;
            for (Entity e : player.getWorld().getEntities()) {
                if (!e.getLocation().getWorld().equals(pl.getWorld())) continue;
                if (e.getLocation().distanceSquared(pl) > r2) continue;
                total++;
                if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
                if (plugin.getStandPresetManager().isPreview(stand)) continue;
                if (tuner.isPluginStand(stand)) {
                    stands++;
                    for (Entity pass : stand.getPassengers()) {
                        if (pass instanceof Display) parts++;
                    }
                }
            }
            int own = stands + parts;
            AntiSpam.send(player, "§6=== Encje w promieniu §e" + (int) radius + "§6 bloków od Ciebie ===");
            AntiSpam.send(player, "§fDekoracje pluginu: §e" + stands + "§f stojaków, §e" + parts
                    + "§f części §7(razem " + own + " encji)");
            AntiSpam.send(player, "§7Wszystkie encje w promieniu: §e" + total
                    + "§7 (łącznie z mobami, przedmiotami itd.)");
            if (own > 500) {
                AntiSpam.send(player, "§cDużo dekoracji w jednym miejscu - słabsze komputery mogą mieć spadki FPS.");
            } else if (own > 200) {
                AntiSpam.send(player, "§eUmiarkowanie - przy granicy komfortu dla słabszych komputerów.");
            } else {
                AntiSpam.send(player, "§aLekko - bez obaw o wydajność.");
            }
            return true;
        }
        if (sub.equals("part")) {
            if (args.length < 2) {
                AntiSpam.send(player, "§cUżyj: /stojak part <blok|przedmiot> [x y z]");
                return true;
            }
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden strojony stojak.");
                return true;
            }
            if (args[1].equalsIgnoreCase("remove")) {
                if (!plugin.getArmorStandTuner().removeSelectedPart(stand, player)) {
                    AntiSpam.send(player, "§cNie wybrano żadnej części (STICKiem do części, potem BLAZE_ROD/BONE/BARRIER).");
                }
                return true;
            }
            // /stojak part reka [x y z] - doczep przedmiot z ręki (CustomModelData, tekstury paczki)
            if (args[1].equalsIgnoreCase("reka") || args[1].equalsIgnoreCase("ręka")
                    || args[1].equalsIgnoreCase("hand")) {
                if (args.length >= 5) {
                    Double x = parseDoubleOr(args[2], null);
                    Double y = parseDoubleOr(args[3], null);
                    Double z = parseDoubleOr(args[4], null);
                    if (x == null || y == null || z == null) {
                        AntiSpam.send(player, "§cUżyj: /stojak part reka [x y z] (liczby)");
                        return true;
                    }
                    plugin.getArmorStandTuner().attachPartFromHand(stand, player, x, y, z);
                } else {
                    plugin.getArmorStandTuner().attachPartFromHand(stand, player);
                }
                return true;
            }
            Material material = Material.matchMaterial(args[1]);
            if (material == null) {
                AntiSpam.send(player, "§cNieznany materiał: " + args[1]);
                return true;
            }
            if (args.length >= 5) {
                Double x = parseDoubleOr(args[2], null);
                Double y = parseDoubleOr(args[3], null);
                Double z = parseDoubleOr(args[4], null);
                if (x == null || y == null || z == null) {
                    AntiSpam.send(player, "§cUżyj: /stojak part <blok|przedmiot> [x y z] (liczby)");
                    return true;
                }
                plugin.getArmorStandTuner().attachPart(stand, player, material, x, y, z);
            } else {
                plugin.getArmorStandTuner().attachPart(stand, player, material);
            }
            return true;
        }
        if (sub.equals("partclear")) {
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden strojony stojak.");
                return true;
            }
            plugin.getArmorStandTuner().clearParts(stand, player);
            return true;
        }
        // Duplikowanie części z identycznymi statystykami (skala/obrót/pozycja):
        //   /stojak duplikuj [materiał] [dx dy dz] - kopia w miejscu oryginału,
        //   przesunięta o dx dy dz (np. duplikuj 0 0 1 = kopia 1 blok dalej)
        if (sub.equals("duplikuj") || sub.equals("duplikat") || sub.equals("duplicate")) {
            String materialName = null;
            Integer partNumber = null;
            double dx = 0, dy = 0, dz = 0;
            int start = 1;
            // /stojak duplikuj czesc <liczba> [dx dy dz] - duplikuj część o numerze
            // z action bara (przewijanie STICKiem pokazuje CZĘŚĆ 1, CZĘŚĆ 2, ...)
            if (args.length >= 3 && (args[1].equalsIgnoreCase("czesc")
                    || args[1].equalsIgnoreCase("część") || args[1].equalsIgnoreCase("part"))) {
                Integer num = parseIntOr(args[2], null);
                if (num == null || num < 1) {
                    AntiSpam.send(player, "§cUżyj: /stojak duplikuj czesc <liczba> [dx dy dz] (liczba = numer części z action bara)");
                    return true;
                }
                partNumber = num;
                start = 3;
            } else if (args.length >= 2 && parseDoubleOr(args[1], null) == null) {
                materialName = args[1];
                start = 2;
            }
            if (materialName != null && Material.matchMaterial(materialName) == null) {
                AntiSpam.send(player, "§cNieznany materiał: " + materialName);
                return true;
            }
            if (args.length >= start + 3) {
                Double x = parseDoubleOr(args[start], null);
                Double y = parseDoubleOr(args[start + 1], null);
                Double z = parseDoubleOr(args[start + 2], null);
                if (x == null || y == null || z == null) {
                    AntiSpam.send(player, "§cUżyj: /stojak duplikuj [czesc <liczba> | materiał] [dx dy dz] (liczby)");
                    return true;
                }
                dx = x;
                dy = y;
                dz = z;
            }
            if (partNumber != null) {
                plugin.getArmorStandTuner().duplicatePartNumber(player, partNumber, dx, dy, dz);
            } else {
                plugin.getArmorStandTuner().duplicatePart(player, materialName, dx, dy, dz);
            }
            return true;
        }
        // Statystyki części (offset/skala/obrót): /stojak stats [materiał]
        if (sub.equals("stats") || sub.equals("statystyki")) {
            String materialName = args.length >= 2 ? args[1] : null;
            if (materialName != null && Material.matchMaterial(materialName) == null) {
                AntiSpam.send(player, "§cNieznany materiał: " + materialName);
                return true;
            }
            plugin.getArmorStandTuner().partStats(player, materialName);
            return true;
        }
        // Informacje o części w liczbach (skala/kordynaty/obrót/skręt/pochylenie):
        // /stojak informacja [materiał] (patrząc na stojak)
        if (sub.equals("informacja") || sub.equals("informacje")) {
            String materialName = args.length >= 2 ? args[1] : null;
            if (materialName != null && Material.matchMaterial(materialName) == null) {
                AntiSpam.send(player, "§cNieznany materiał: " + materialName);
                return true;
            }
            plugin.getArmorStandTuner().partInformation(player, materialName);
            return true;
        }
        // Ustawianie wyposażenia komendą: /stojak head|hand|offhand <materiał|brak>
        // (działa na stojaku, na który patrzysz, albo AKTYWNYM)
        if (sub.equals("head") || sub.equals("hand") || sub.equals("offhand")) {
            if (args.length < 2) {
                AntiSpam.send(player, "§cUżyj: /stojak " + sub + " <materiał|brak> (np. /stojak " + sub + " stone)");
                return true;
            }
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            ItemStack item;
            if (args[1].equalsIgnoreCase("brak") || args[1].equalsIgnoreCase("none")
                    || args[1].equalsIgnoreCase("air") || args[1].equalsIgnoreCase("usun")
                    || args[1].equalsIgnoreCase("remove")) {
                item = new ItemStack(Material.AIR); // zdejmij
            } else {
                Material m = Material.matchMaterial(args[1]);
                if (m == null) {
                    AntiSpam.send(player, "§cNieznany materiał: " + args[1]);
                    return true;
                }
                item = new ItemStack(m);
            }
            String slot = switch (sub) {
                case "hand" -> "ręka (main hand)";
                case "offhand" -> "offhand";
                default -> "głowa";
            };
            switch (sub) {
                case "head" -> stand.getEquipment().setHelmet(item);
                case "hand" -> stand.getEquipment().setItemInMainHand(item);
                default -> stand.getEquipment().setItemInOffHand(item);
            }
            AntiSpam.send(player, "§aUstawiono " + slot + ": §f"
                    + (item.getType() == Material.AIR ? "brak" : item.getType().name().toLowerCase()) + "§a.");
            return true;
        }
        // Cofanie usunięcia części: /stojak undo (przywraca ostatnio usuniętą część/części)
        if (sub.equals("undo")) {
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            int n = plugin.getArmorStandTuner().undoParts(stand, player);
            if (n > 0) {
                AntiSpam.sendActionBar(player, "§aCofnięto zmianę - przywrócono " + n + " " + (n == 1 ? "część" : "części")
                        + "§a. §7(/stojak undo dalej; /stojak ponow przywraca poprzedni stan)");
            } else if (n < 0) {
                AntiSpam.sendActionBar(player, "§aCofnięto doczepienie - usunięto ostatnio dodaną część. §7(/stojak undo dalej; /stojak ponow przywraca poprzedni stan)");
            } else {
                AntiSpam.sendActionBar(player, "§7Brak zmian do cofnięcia (undo puste).");
            }
            return true;
        }
        // /stojak ponow (redo) - przywraca stan części sprzed ostatniego /stojak undo;
        // ratuje sytuację, gdy undo zdjęło wszystkie części (np. pierwsze doczepienie)
        if (sub.equals("ponow") || sub.equals("redo") || sub.equals("przywroc_parts")
                || sub.equals("przywroc_czesci")) {
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            int n = plugin.getArmorStandTuner().redoParts(stand, player);
            if (n > 0) {
                AntiSpam.sendActionBar(player, "§aPrzywrócono stan sprzed cofnięcia - " + n + " "
                        + (n == 1 ? "część" : "części") + " wróciła.");
            } else if (n < 0) {
                AntiSpam.sendActionBar(player, "§aPrzywrócono stan sprzed cofnięcia - wszystkie części usunięte (stan był pusty).");
            } else {
                AntiSpam.sendActionBar(player, "§7Brak stanu do przywrócenia - najpierw użyj /stojak undo.");
            }
            return true;
        }
        if (sub.equals("save")) {
            if (args.length < 2) {
                AntiSpam.send(player, "§cUżyj: /stojak save <nazwa>");
                return true;
            }
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden strojony stojak.");
                return true;
            }
            if (plugin.getStandPresetManager().save(stand, args[1], sinkParam(args))) {
                AntiSpam.send(player, "§aZapisano stojak jako §e" + args[1] + "§a.");
            } else {
                AntiSpam.send(player, "§cNie udało się zapisać stojaka.");
            }
            return true;
        }
        if (sub.equals("saveitem")) {
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden strojony stojak.");
                return true;
            }
            if (plugin.getStandPresetManager().saveToItem(player, stand, sinkParam(args))) {
                AntiSpam.send(player, "§aZapisano wygląd stojaka na przedmiot w ręce. Prawy klik = postaw.");
            } else {
                AntiSpam.send(player, "§cMusisz trzymać w ręce przedmiot, na który chcesz zapisać.");
            }
            return true;
        }
        if (sub.equals("offset")) {
            if (args.length >= 4) {
                Double x = parseDoubleOr(args[1], null);
                Double y = parseDoubleOr(args[2], null);
                Double z = parseDoubleOr(args[3], null);
                if (x == null || y == null || z == null) {
                    AntiSpam.send(player, "§cUżyj: /stojak offset <x> <y> <z>");
                    return true;
                }
                plugin.getStandPresetManager().setPlacementOffset(x, y, z);
                AntiSpam.send(player, "§aOffset stawiania ustawiony: " + x + " " + y + " " + z);
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("reset")) {
                plugin.getStandPresetManager().setPlacementOffset(0, 0, 0);
                AntiSpam.send(player, "§aOffset stawiania wyzerowany.");
                return true;
            }
            double[] o = plugin.getStandPresetManager().getPlacementOffset();
            AntiSpam.send(player, "§aObecny offset: " + o[0] + " " + o[1] + " " + o[2]
                    + " §7(użyj: /stojak offset <x> <y> <z> albo /stojak offset reset)");
            return true;
        }
        if (sub.equals("place")) {
            if (args.length < 2) {
                AntiSpam.send(player, "§cUżyj: /stojak place <nazwa> [x y z]");
                return true;
            }
            Location exact = null;
            if (args.length >= 5) {
                Double x = parseDoubleOr(args[2], null);
                Double y = parseDoubleOr(args[3], null);
                Double z = parseDoubleOr(args[4], null);
                if (x == null || y == null || z == null) {
                    AntiSpam.send(player, "§cUżyj: /stojak place <nazwa> [x y z]");
                    return true;
                }
                exact = new Location(player.getWorld(), x, y, z);
            }
            ArmorStand placed = plugin.getStandPresetManager().place(args[1], player, null, exact);
            if (placed != null) {
                plugin.getArmorStandTuner().setActive(player, placed);
                AntiSpam.send(player, "§aPostawiono stojak §e" + args[1] + "§a (aktywny).");
            } else {
                AntiSpam.send(player, "§cNie znaleziono zapisu §e" + args[1] + "§c.");
            }
            return true;
        }
        if (sub.equals("item")) {
            if (args.length < 2) {
                AntiSpam.send(player, "§cUżyj: /stojak item <nazwa>");
                return true;
            }
            ItemStack item = plugin.getStandPresetManager().bindItem(args[1]);
            if (item == null) {
                AntiSpam.send(player, "§cNie znaleziono zapisu §e" + args[1] + "§c.");
                return true;
            }
            player.getInventory().setItemInMainHand(item);
            AntiSpam.send(player, "§aDostałeś przedmiot postawiający stojak §e" + args[1] + "§a (prawy klik).");
            return true;
        }
        if (sub.equals("list")) {
            java.util.List<String> names = plugin.getStandPresetManager().listPresets();
            if (names.isEmpty()) {
                AntiSpam.send(player, "§7Brak zapisanych stojaków.");
                return true;
            }
            player.sendMessage(Component.text("§6Zapisane stojaki: §f" + String.join(", ", names)));
            return true;
        }
        if (sub.equals("delete")) {
            // Usuwanie zapisu wymaga potwierdzenia słowem confirm, żeby nie dało się
            // czegoś skasować przez przypadek. /stojak delete <nazwa> tylko pokazuje
            // ostrzeżenie i zapamiętuje prośbę na 20 s, ale /stojak delete <nazwa>
            // confirm działa OD RAZU - celowo wpisane „confirm" samo jest potwierdzeniem.
            if (args.length < 2) {
                AntiSpam.send(player, "§cUżyj: §e/stojak delete <nazwa> confirm§c "
                        + "(usunie zapis ORAZ postawione stojaki o tej nazwie).");
                return true;
            }
            String name = args[1];
            boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
            if (!confirmed) {
                // ostrzeżenie + zapamiętanie prośby (nie usuwa niczego)
                pendingDeletes.put(player.getUniqueId(),
                        new PendingDelete(name, System.currentTimeMillis() + DELETE_CONFIRM_MS));
                AntiSpam.send(player, "§eUsunięcie zapisu §f" + name + "§e (ważna 20 s). "
                        + "Potwierdź: §6/stojak delete " + name + " confirm");
                return true;
            }
            // potwierdzenie: samo „confirm" wystarcza, nie trzeba wcześniej wpisywać
            // /stojak delete <nazwa> - celowo wpisane słowo to ochrona przed przypadkiem
            pendingDeletes.remove(player.getUniqueId());
            // usuwa ZAPIS (plik presetu) ORAZ wszystkie postawione stojaki o tej
            // nazwie (custom name) - dopiero po wpisaniu confirm
            boolean presetDeleted = plugin.getStandPresetManager().delete(name);
            int removedStands = 0;
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                for (Entity e : new ArrayList<>(w.getEntities())) {
                    if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
                    if (plugin.getStandPresetManager().isPreview(stand)) continue;
                    String n = stand.getCustomName();
                    if (n != null && !n.isEmpty() && n.equalsIgnoreCase(name)) {
                        plugin.getArmorStandTuner().remove(stand, player, false);
                        removedStands++;
                    }
                }
            }
            String count = removedStands + (removedStands == 1 ? " postawiony stojak" : " postawione stojaki");
            if (presetDeleted && removedStands > 0) {
                AntiSpam.send(player, "§aUsunięto zapis §e" + name + "§a oraz " + count + " o tej nazwie.");
            } else if (presetDeleted) {
                AntiSpam.send(player, "§aUsunięto zapis §e" + name + "§a.");
            } else if (removedStands > 0) {
                AntiSpam.send(player, "§aNie było zapisu §e" + name + "§a, ale usunięto " + count + " o tej nazwie.");
            } else {
                AntiSpam.send(player, "§cNie znaleziono zapisu §e" + name + "§c ani postawionych stojaków o tej nazwie.");
            }
            return true;
        }
        if (sub.equals("copy")) {
            ArmorStand stand = plugin.getArmorStandTuner().findLookedStand(player, false);
            if (stand == null) {
                stand = plugin.getArmorStandTuner().getActive(player);
                if (stand != null) {
                    AntiSpam.send(player, "§7Nie patrzysz na żaden stojak - kopiuję AKTYWNY stojak.");
                }
            }
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            if (plugin.getStandPresetManager().copyStand(stand)) {
                plugin.getArmorStandTuner().setActive(player, stand);
                int n = plugin.getArmorStandTuner().getParts(stand).size();
                AntiSpam.send(player, "§aSkopiowano stojak" + (n > 0 ? " (" + n + " części)" : "")
                        + ". §e/stojak paste§a, żeby postawić kopię.");
            } else {
                AntiSpam.send(player, "§cNie udało się skopiować stojaka.");
            }
            return true;
        }
        if (sub.equals("paste")) {
            Location exact = null;
            if (args.length >= 4) {
                Double x = parseDoubleOr(args[1], null);
                Double y = parseDoubleOr(args[2], null);
                Double z = parseDoubleOr(args[3], null);
                if (x == null || y == null || z == null) {
                    AntiSpam.send(player, "§cUżyj: /stojak paste [x y z]");
                    return true;
                }
                exact = new Location(player.getWorld(), x, y, z);
            }
            ArmorStand pasted = plugin.getStandPresetManager().pasteStand(player, exact);
            if (pasted != null) {
                plugin.getArmorStandTuner().setActive(player, pasted);
                AntiSpam.send(player, "§aPostawiono kopię stojaka (aktywna).");
            } else {
                AntiSpam.send(player, "§cNajpierw skopiuj stojak: /stojak copy (albo nie patrzysz na żaden blok).");
            }
            return true;
        }
        // Precyzyjne przesuwanie doczepionej części:
        //   /stojak <materiał> move <x> <y> <z> - wybiera część z materiału i przesuwa
        //   /stojak <materiał>_<liczba> move <x> <y> <z> - przesuwa KONKRETNĄ część (np. stone_2)
        //   /stojak <nazwa> move <x> <y> <z> - to samo na stojaku o nazwie (bez patrzenia)
        if (args.length >= 2 && args[1].equalsIgnoreCase("move") && !sub.equals("move")) {
            if (args.length < 5) {
                AntiSpam.send(player, "§cUżyj: /stojak move ([część_liczba]) <x y z>");
                return true;
            }
            return handleMove(player, resolveMaterialOrNamed(player, args[0]), null, args[2], args[3], args[4]);
        }
        //   /stojak move <x> <y> <z> - przesuwa aktualnie wybraną część
        //   /stojak move <materiał> <x> <y> <z> - wybiera część z materiału i przesuwa
        //   /stojak move czesc <liczba> <x> <y> <z> - przesuwa KONKRETNĄ część (numer z action bara)
        //   /stojak move <materiał>_<liczba> <x> <y> <z> - przesuwa N-tą część z materiału (np. stone_2)
        if (sub.equals("move")) {
            if (args.length >= 2 && (args[1].equalsIgnoreCase("czesc")
                    || args[1].equalsIgnoreCase("część") || args[1].equalsIgnoreCase("part"))) {
                if (args.length < 6) {
                    AntiSpam.send(player, "§cUżyj: /stojak move ([część_liczba]) <x y z>");
                    return true;
                }
                Integer num = parseIntOr(args[2], null);
                if (num == null || num < 1) {
                    AntiSpam.send(player, "§cUżyj: /stojak move ([część_liczba]) <x y z>");
                    return true;
                }
                return handleMove(player, null, num, args[3], args[4], args[5]);
            }
            if (args.length < 4) {
                AntiSpam.send(player, "§cUżyj: /stojak move ([część_liczba]) <x y z>");
                return true;
            }
            if (args.length >= 5 && parseDoubleOr(args[1], null) == null) {
                return handleMove(player, args[1], null, args[2], args[3], args[4]);
            }
            return handleMove(player, null, null, args[1], args[2], args[3]);
        }
        // Ustawienie części na blok, na który patrzysz (bez liczenia delty):
        //   /stojak tam [materiał] - wybrana część (albo z materiału) ląduje na bloku pod celownikiem
        if (sub.equals("tam")) {
            String materialName = args.length >= 2 ? args[1] : null;
            if (materialName != null && Material.matchMaterial(materialName) == null) {
                AntiSpam.send(player, "§cNieznany materiał: " + materialName);
                return true;
            }
            plugin.getArmorStandTuner().moveSelectedPartToLookedBlock(player, materialName);
            return true;
        }
        // Skalowanie części: /stojak [<materiał>|<nazwa>] scale [<wartość>|<x> <y> <z>|reset]
        if (args.length >= 2 && args[1].equalsIgnoreCase("scale") && !sub.equals("scale")) {
            return handleScale(player, resolveMaterialOrNamed(player, args[0]), args, 2);
        }
        if (sub.equals("scale")) {
            return handleScale(player, null, args, 1);
        }
        // Obrót CAŁEGO stojaka: /stojak rotate [<x> <y> <z>|reset] (yaw/pitch + części)
        // Obrót CZĘŚCI: /stojak [<materiał>|<nazwa>] rotate [<x> <y> <z>|reset]
        if (args.length >= 2 && args[1].equalsIgnoreCase("rotate") && !sub.equals("rotate")) {
            return handleRotate(player, resolveMaterialOrNamed(player, args[0]), args, 2);
        }
        if (sub.equals("rotate")) {
            return handleRotate(player, null, args, 1);
        }
        // Zmiana materiału części: /stojak zamien <nowy> | /stojak zamien czesc <stary> <nowy>
        // czesc = KONKRETNA część: ta, na którą gracz patrzy (jeśli pasuje do materiału),
        // dopiero potem pierwsza część z tego materiału. Dodatkowo da się wskazać
        // KONKRETNĄ część po numerze (czesc <liczba>) albo po materiale z numerem
        // (<materiał>_<liczba>, np. stone_2): /stojak zamien czesc <liczba> <nowy>
        // oraz /stojak zamien <materiał>_<liczba> <nowy>.
        if (sub.equals("zamien") || sub.equals("zmien") || sub.equals("change")) {
            if (args.length < 2 || args.length > 4) {
                AntiSpam.send(player, "§cUżyj: /stojak zamien część <nowy materiał>");
                return true;
            }
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            ArmorStandTuner tuner = plugin.getArmorStandTuner();
            boolean partMode = args.length >= 3 && (args[1].equalsIgnoreCase("czesc")
                    || args[1].equalsIgnoreCase("część") || args[1].equalsIgnoreCase("cześć")
                    || args[1].equalsIgnoreCase("part"));
            if (partMode) {
                if (args.length != 4) {
                    AntiSpam.send(player, "§cUżyj: /stojak zamien część <nowy materiał>");
                    return true;
                }
                Integer num = parseIntOr(args[2], null);
                if (num != null && num >= 1) {
                    // KONKRETNA część po numerze z action bara (CZĘŚĆ 1, CZĘŚĆ 2, ...)
                    if (!tuner.selectPartNumber(stand, num, null)) {
                        int total = tuner.getParts(stand).size();
                        AntiSpam.send(player, "§cTen stojak ma " + total + " "
                                + (total == 1 ? "część" : "części")
                                + " - nie ma części nr " + num + ".");
                        return true;
                    }
                } else {
                    Material oldMat = Material.matchMaterial(args[2]);
                    if (oldMat == null) {
                        AntiSpam.send(player, "§cUżyj: /stojak zamien część <nowy materiał>");
                        return true;
                    }
                    if (!tuner.selectPartForOperation(player, stand, args[2])) {
                        AntiSpam.send(player, "§cTen stojak nie ma części z materiału: " + args[2]);
                        return true;
                    }
                }
            } else if (args.length == 3) {
                // /stojak zamien <materiał>_<liczba> <nowy> - KONKRETNA część z materiału
                MatNum mn = parseMaterialNumber(args[1]);
                if (mn == null) {
                    AntiSpam.send(player, "§cUżyj: /stojak zamien część <nowy materiał>");
                    return true;
                }
                Material m = Material.matchMaterial(mn.matName);
                if (!tuner.selectPartNumber(stand, mn.number, m)) {
                    int total = tuner.getParts(stand).size();
                    AntiSpam.send(player, "§cBrak części nr §e" + mn.number + "§c z materiału §e"
                            + mn.matName + "§c (ten stojak ma " + total + " "
                            + (total == 1 ? "część" : "części") + ").");
                    return true;
                }
            } else if (!tuner.hasSelectedPart(stand)) {
                AntiSpam.send(player, "§cNie wybrano części. Wybierz STICKiem albo podaj: /stojak zamien czesc <liczba|stary materiał> <nowy materiał>");
                return true;
            }
            Material newMat = Material.matchMaterial(args[args.length - 1]);
            if (newMat == null) {
                AntiSpam.send(player, "§cNieznany materiał: " + args[args.length - 1]);
                return true;
            }
            if (newMat == Material.AIR) {
                AntiSpam.send(player, "§cNie można zmienić części na powietrze - usuń ją zamiast tego: /stojak part remove");
                return true;
            }
            tuner.changeSelectedPartMaterial(stand, player, newMat);
            return true;
        }
        // Lustrzane odbicie całego stojaka (wszystkie części naraz): /stojak lustro [x|z]
        // Lustrzana KOPIA WYBRANEJ części po drugiej stronie: /stojak lustro czesc [x|z]
        if (sub.equals("lustro") || sub.equals("mirror")) {
            if (args.length >= 2 && (args[1].equalsIgnoreCase("czesc")
                    || args[1].equalsIgnoreCase("część") || args[1].equalsIgnoreCase("part"))) {
                String axis = args.length >= 3 ? args[2] : "x";
                if (!axis.equalsIgnoreCase("x") && !axis.equalsIgnoreCase("z")) {
                    AntiSpam.send(player, "§cUżyj: /stojak lustro czesc [x|z] (domyślnie X)");
                    return true;
                }
                plugin.getArmorStandTuner().mirrorDuplicateSelected(player, axis);
                return true;
            }
            String axis = args.length >= 2 ? args[1] : "x";
            if (!axis.equalsIgnoreCase("x") && !axis.equalsIgnoreCase("z")) {
                AntiSpam.send(player, "§cUżyj: /stojak lustro [x|z] albo /stojak lustro czesc [x|z]");
                return true;
            }
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            plugin.getArmorStandTuner().mirrorStand(stand, player, axis);
            return true;
        }
        // Aliasy lustro czesc: /stojak odbicie|odbij|mirrorpart|lustroczesc [x|z]
        // (kopia ląduje w odbitym położeniu - offset, skala, obroty).
        if (sub.equals("odbicie") || sub.equals("odbij") || sub.equals("mirrorpart")
                || sub.equals("lustroczesc") || sub.equals("lustro_czesc")) {
            String axis = args.length >= 2 ? args[1] : "x";
            if (!axis.equalsIgnoreCase("x") && !axis.equalsIgnoreCase("z")) {
                AntiSpam.send(player, "§cUżyj: /stojak odbicie [x|z] (domyślnie X)");
                return true;
            }
            plugin.getArmorStandTuner().mirrorDuplicateSelected(player, axis);
            return true;
        }

        if (sub.equals("glow")) {
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            if (args.length >= 2 && (args[1].equalsIgnoreCase("off") || args[1].equals("0"))) {
                plugin.getArmorStandTuner().glowOff(stand, player);
                return true;
            }
            int seconds = 10;
            if (args.length >= 2) {
                try {
                    seconds = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    AntiSpam.send(player, "§cUżyj: /stojak glow [sekundy] | /stojak glow off");
                    return true;
                }
            }
            plugin.getArmorStandTuner().glow(stand, player, seconds);
            return true;
        }
        if (sub.equals("aktywny")) {
            ArmorStandTuner tuner = plugin.getArmorStandTuner();
            ArmorStand stand = tuner.getActive(player);
            if (stand == null) {
                AntiSpam.send(player, "§7Nie masz aktywnego stojaka. Postaw/wklej/dołącz jakiś: §e/stojak stworz§7, §eplace <nazwa>§7, §epaste§7, §ebind§7 albo kliknij stojak.");
                return true;
            }
            Location l = stand.getLocation();
            String nm = stand.getCustomName();
            String show = (nm != null && !nm.isEmpty()) ? nm
                    : l.getWorld().getName() + " " + fmt(l.getX()) + " " + fmt(l.getY()) + " " + fmt(l.getZ());
            AntiSpam.send(player, "§aAktywny stojak: §f" + show
                    + " §7(części: " + tuner.getParts(stand).size() + ")");
            return true;
        }
        // Widoczność nazwy: /stojak nazwa on|off (pokaz/ukryj) - NIE zmienia samej
        // nazwy, tylko jej wyświetlanie nad stojakiem. Samą nazwę nadaje /stojak nazwij.
        if (sub.equals("nazwa") || sub.equals("name")) {
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            if (args.length < 2) {
                String cur = stand.getCustomName();
                boolean vis = stand.isCustomNameVisible();
                if (cur == null || cur.isEmpty()) {
                    AntiSpam.send(player, "§7Stojak nie ma nazwy. Użyj: §e/stojak nazwij <nazwa>§7, a potem §e/stojak nazwa on/off§7 (widoczność).");
                } else {
                    AntiSpam.send(player, "§aNazwa stojaka: §f" + cur
                            + "§7 - widoczność: " + (vis ? "§aon" : "§7off")
                            + "§7. Zmiana widoczności: §e/stojak nazwa on|off");
                }
                return true;
            }
            String opt = args[1];
            boolean on = opt.equalsIgnoreCase("on") || opt.equalsIgnoreCase("pokaz")
                    || opt.equalsIgnoreCase("show") || opt.equalsIgnoreCase("widoczna")
                    || opt.equalsIgnoreCase("tak") || opt.equalsIgnoreCase("1");
            boolean off = opt.equalsIgnoreCase("off") || opt.equalsIgnoreCase("ukryj")
                    || opt.equalsIgnoreCase("hide") || opt.equalsIgnoreCase("niewidoczna")
                    || opt.equalsIgnoreCase("nie") || opt.equalsIgnoreCase("0");
            if (on || off) {
                stand.setCustomNameVisible(on);
                AntiSpam.send(player, on
                        ? "§aNazwa stojaka jest teraz WIDOCZNA nad stojakiem."
                        : "§7Nazwa stojaka ukryta (sama nazwa zostaje - włączysz: §e/stojak nazwa on§7).");
                return true;
            }
            if (opt.equalsIgnoreCase("usun") || opt.equalsIgnoreCase("remove")
                    || opt.equalsIgnoreCase("-")) {
                stand.setCustomName(null);
                stand.setCustomNameVisible(false);
                AntiSpam.send(player, "§7Usunięto nazwę stojaka.");
                plugin.getArmorStandTuner().refreshStatusBar(player); // koordynaty wracają do boss bara
                return true;
            }
            AntiSpam.send(player, "§cUżyj: /stojak nazwa on|off (widoczność nazwy). Samą nazwę nadaje: /stojak nazwij <nazwa>");
            return true;
        }
        // Nazwa stojaka: /stojak nazwij <nazwa> - nadaje nazwę stojakowi, na który
        // patrzysz (albo aktywnemu). Nazwa jest od razu widoczna nad stojakiem;
        // ukryjesz ją /stojak nazwa off. Działa też w komendach /stojak <nazwa> ...
        if (sub.equals("nazwij") || sub.equals("nadaj_nazwe") || sub.equals("nadaj")) {
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            if (args.length < 2) {
                AntiSpam.send(player, "§cUżyj: /stojak nazwij <nazwa> (np. /stojak nazwij moja wieżyczka)");
                return true;
            }
            String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
            String name = String.join(" ", rest);
            stand.setCustomName(name);
            stand.setCustomNameVisible(true);
            plugin.getArmorStandTuner().refreshStatusBar(player); // koordynaty w boss barze -> nazwa
            AntiSpam.send(player, "§aNadano stojakowi nazwę: §f" + name
                    + "§a (widoczna nad stojakiem; ukryjesz: §e/stojak nazwa off§a).");
            return true;
        }
        if (sub.equals("visible") || sub.equals("niewidoczny") || sub.equals("niewidzialny")) {
            ArmorStand stand = plugin.getArmorStandTuner().findLookedStand(player, false);
            if (stand == null) {
                stand = plugin.getArmorStandTuner().getActive(player);
                if (stand != null) {
                    AntiSpam.send(player, "§7Nie patrzysz na żaden stojak - zmieniam widoczność AKTYWNEGO stojaka.");
                }
            }
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            boolean show;
            if (args.length >= 2) {
                switch (args[1].toLowerCase()) {
                    case "on", "pokaż", "pokaz", "tak", "1", "true" -> show = true;
                    case "off", "ukryj", "nie", "0", "false" -> show = false;
                    default -> {
                        AntiSpam.send(player, "§cUżyj: /stojak visible [on|off] (albo pokaż|ukryj)");
                        return true;
                    }
                }
            } else {
                show = !stand.isVisible(); // bez argumentu przełącz
            }
            stand.setVisible(show);
            AntiSpam.send(player, show
                    ? "§aStojak jest teraz WIDOCZNY."
                    : "§7Stojak jest teraz NIEWIDOCZNY (invisible).");
            return true;
        }
        // Funkcje stojaka: /stojak funkcja siedzenie [wysokosc] - oznacza istniejacy
        // stojak jako krzeslo na podanej wysokosci. Nie tworzy nowego stojaka.
        if (sub.equals("funkcja") || sub.equals("funkcje")) {
            if (args.length < 2) {
                AntiSpam.send(player, "§cUżyj: /stojak funkcja siedzenie [wysokosc]");
                return true;
            }
            if (args[1].equalsIgnoreCase("siedzenie") || args[1].equalsIgnoreCase("krzeslo")
                    || args[1].equalsIgnoreCase("chair")) {
                ArmorStand stand = tunedStand(player);
                if (stand == null) {
                    AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                    return true;
                }
                double seatHeight = 0.0;
                if (args.length >= 3) {
                    Double h = parseDoubleOr(args[2], null);
                    if (h == null) {
                        AntiSpam.send(player, "§cUżyj: /stojak funkcja siedzenie [wysokosc] (liczba)");
                        return true;
                    }
                    seatHeight = h;
                }
                plugin.getArmorStandTuner().setSeat(stand, player, true);
                plugin.getArmorStandTuner().setSeatYOffset(stand, seatHeight);
                AntiSpam.send(player, "§aOznaczono stojak jako krzesło" + (seatHeight != 0 ? " na wysokości §e" + fmt(seatHeight) : "") + "§a. Kliknij = siadanie, Shift = wstań.");
                return true;
            }
            // /stojak funkcja tv [szerokość] [wysokość] - czarny ekran "telewizora" z
            // czarnych banerów ustawionych obok siebie; [x y z] na końcu = dokładne miejsce
            if (args[1].equalsIgnoreCase("tv") || args[1].equalsIgnoreCase("telewizor")
                    || args[1].equalsIgnoreCase("ekran")) {
                int width = 3, height = 2;
                Location loc;
                if (args.length >= 5) {
                    // dokładne koordynaty (x y z = blok: środek XZ, y = stopy)
                    Double x = parseDoubleOr(args[2], null);
                    Double y = parseDoubleOr(args[3], null);
                    Double z = parseDoubleOr(args[4], null);
                    if (x == null || y == null || z == null) {
                        AntiSpam.send(player, "§cUżyj: /stojak funkcja tv [szerokość] [wysokość] albo /stojak funkcja tv <x> <y> <z>");
                        return true;
                    }
                    loc = new Location(player.getWorld(), x + 0.5, y, z + 0.5);
                } else {
                    if (args.length >= 3) {
                        Integer w = parseIntOr(args[2], null);
                        if (w == null || w < 1 || w > 12) {
                            AntiSpam.send(player, "§cUżyj: /stojak funkcja tv [szerokość 1-12] [wysokość 1-12]");
                            return true;
                        }
                        width = w;
                    }
                    if (args.length >= 4) {
                        Integer h = parseIntOr(args[3], null);
                        if (h == null || h < 1 || h > 12) {
                            AntiSpam.send(player, "§cUżyj: /stojak funkcja tv [szerokość 1-12] [wysokość 1-12]");
                            return true;
                        }
                        height = h;
                    }
                    org.bukkit.block.Block b = player.getTargetBlockExact(100);
                    if (b == null) {
                        AntiSpam.send(player, "§cNie patrzysz na żaden blok - popatrz na miejsce albo podaj koordynaty: /stojak funkcja tv <x> <y> <z>");
                        return true;
                    }
                    loc = b.getLocation().add(0.5, 1, 0.5); // wierzch bloku, środek XZ
                }
                plugin.getArmorStandTuner().spawnTv(loc, player, width, height);
                AntiSpam.send(player, "§aPostawiono czarny ekran TV §e" + width + "×" + height
                        + "§a (" + (width * height) + " banerów) na §e" + fmt(loc.getX()) + " "
                        + fmt(loc.getY()) + " " + fmt(loc.getZ())
                        + "§a. Edytujesz jak normalny stojak - zapisz: §e/stojak save <nazwa>");
                return true;
            }
            // /stojak funkcja lampa [wysokość] - wizualna lampa: 1 = sam świecący blok,
            // 2 = ciemny słup + głowa (działa też 3, 4). Stoi na bloku pod celownikiem.
            if (args[1].equalsIgnoreCase("lampa") || args[1].equalsIgnoreCase("lamp")
                    || args[1].equalsIgnoreCase("latarnia")) {
                int height = 1;
                if (args.length >= 3) {
                    Integer h = parseIntOr(args[2], null);
                    if (h == null || h < 1 || h > 4) {
                        AntiSpam.send(player, "§cUżyj: /stojak funkcja lampa [wysokość 1-4]");
                        return true;
                    }
                    height = h;
                }
                org.bukkit.block.Block b = player.getTargetBlockExact(100);
                if (b == null) {
                    AntiSpam.send(player, "§cNie patrzysz na żaden blok - popatrz na miejsce dla lampy.");
                    return true;
                }
                Location loc = b.getLocation().add(0.5, 1, 0.5); // wierzch bloku, środek XZ
                plugin.getArmorStandTuner().spawnLamp(loc, player, height);
                AntiSpam.send(player, "§aPostawiono lampę §e" + height + "§a bloków na §e"
                        + fmt(loc.getX()) + " " + fmt(loc.getY()) + " " + fmt(loc.getZ())
                        + "§a. Edytujesz jak normalny stojak - zapisz: §e/stojak save <nazwa>");
                return true;
            }
            // Klik-przełącznik stanów: /stojak funkcja zmiana <stanA> <stanB> -
            // kliknięcie stojaka PODMIENIA części między dwoma zapisanymi presetami
            // (z płynnym przejściem), kolejne kliknięcie wraca do stanu początkowego.
            // /stojak funkcja zmiana off - usuwa powiązanie.
            if (args[1].equalsIgnoreCase("zmiana") || args[1].equalsIgnoreCase("stan")
                    || args[1].equalsIgnoreCase("przelacznik") || args[1].equalsIgnoreCase("przełącznik")) {
                ArmorStand stand = tunedStand(player);
                if (stand == null) {
                    AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                    return true;
                }
                if (args.length >= 3 && (args[2].equalsIgnoreCase("off")
                        || args[2].equalsIgnoreCase("usun") || args[2].equalsIgnoreCase("remove"))) {
                    plugin.getArmorStandTuner().clearStates(stand);
                    AntiSpam.send(player, "§7Usunięto powiązanie stanów - stojak nie zmienia się po kliknięciu.");
                    return true;
                }
                if (args.length < 4) {
                    AntiSpam.send(player, "§cUżyj: /stojak funkcja zmiana <stanA> <stanB> (nazwy zapisów z /stojak save <nazwa>)");
                    return true;
                }
                String a = args[2];
                String b = args[3];
                if (plugin.getStandPresetManager().loadPreset(a) == null) {
                    AntiSpam.send(player, "§cBrak zapisu stanu A: §e" + a
                            + "§c. Najpierw: /stojak save " + a);
                    return true;
                }
                if (plugin.getStandPresetManager().loadPreset(b) == null) {
                    AntiSpam.send(player, "§cBrak zapisu stanu B: §e" + b
                            + "§c. Najpierw: /stojak save " + b);
                    return true;
                }
                plugin.getArmorStandTuner().setStates(stand, a, b);
                AntiSpam.send(player, "§aStojak = klik-przełącznik: §e" + a + "§a <-> §e" + b
                        + "§a. Kliknięcie zmienia stan (płynne przejście), kolejne wraca."
                        + " Zapisz całość: §e/stojak save <nazwa>§a, żeby powiązanie przetrwało.");
                return true;
            }
            // /stojak funkcja glowka <nazwa> - daje głównkę z custom teksturą do ręki
            if (args[1].equalsIgnoreCase("głowka") || args[1].equalsIgnoreCase("glowka")
                    || args[1].equalsIgnoreCase("head")) {
                if (args.length < 3) {
                    AntiSpam.send(player, "§cUżyj: /stojak funkcja głowka <nazwa>");
                    pl.vd.stand.HeadsManager hm = plugin.getHeadsManager();
                    for (String name : hm.getHeadNames()) {
                        AntiSpam.send(player, "  §f" + name);
                    }
                    return true;
                }
                pl.vd.stand.HeadsManager hm = plugin.getHeadsManager();
                ItemStack head = hm.getHead(args[2]);
                if (head == null) {
                    AntiSpam.send(player, "§cNie znaleziono główki §e" + args[2] + "§c. Dostępne:");
                    for (String name : hm.getHeadNames()) {
                        AntiSpam.send(player, "  §f" + name);
                    }
                    return true;
                }
                // daj główkę do pustego slotu w ekwipunku lub na kursor
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(head);
                if (leftover.isEmpty()) {
                    AntiSpam.send(player, "§aDano główkę §e" + args[2] + "§a do ekwipunku.");
                } else {
                    // ekwipunek pełny — upuść na ziemię
                    player.getWorld().dropItemNaturally(player.getLocation(), head);
                    AntiSpam.send(player, "§aEkwipunek pełny — główka §e" + args[2] + "§a upuszczona na ziemię.");
                }
                return true;
            }
            AntiSpam.send(player, "§cNieznana funkcja: §e" + args[1] + "§c. Dostępne: §esiedzenie§c, §etv§c, §elampa§c, §egłowka§c, §ezmiana <stanA> <stanB>§c.");
            return true;
        }
        // Krzesło ze stojaka: /stojak krzeslo [<nazwa>] [on|off] - oznacz ISTNIEJĄCY
        // stojak jako siedzisko (np. zbudowane krzesło z części); kliknięcie = siadanie.
        // Z <nazwa>: stojak dostaje tę widoczną nazwę i siadać można TYLKO na stojaku
        // z taką nazwą (np. /stojak krzeslo moje_krzeslo on).
        if (sub.equals("krzeslo") || sub.equals("chair")) {
            ArmorStand stand = tunedStand(player);
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
                return true;
            }
            // pierwszy argument to nazwa, gdy nie jest flagą on/off
            String seatName = null;
            int idx = 1;
            if (args.length > 1) {
                String a1 = args[1];
                boolean flag = a1.equalsIgnoreCase("on") || a1.equalsIgnoreCase("wlacz") || a1.equalsIgnoreCase("włącz")
                        || a1.equalsIgnoreCase("tak") || a1.equalsIgnoreCase("1") || a1.equalsIgnoreCase("true")
                        || a1.equalsIgnoreCase("off") || a1.equalsIgnoreCase("wylacz") || a1.equalsIgnoreCase("wyłącz")
                        || a1.equalsIgnoreCase("nie") || a1.equalsIgnoreCase("0") || a1.equalsIgnoreCase("false");
                if (!flag) {
                    seatName = args[1];
                    idx = 2;
                }
            }
            boolean on = args.length <= idx || !(args[idx].equalsIgnoreCase("off")
                    || args[idx].equalsIgnoreCase("nie") || args[idx].equalsIgnoreCase("0")
                    || args[idx].equalsIgnoreCase("false"));
            plugin.getArmorStandTuner().setSeat(stand, player, on, seatName);
            return true;
        }
        // Zegar ze stojaka: /stojak zegar test|anuluj - podgląd ruchu wskazówek
        // bez areny (test: 1 minuta przyspieszona 2.5×, 24 minuty = godzina).
        if (sub.equals("zegar")) {
            if (args.length < 2) {
                AntiSpam.send(player, "§cUżyj: §e/stojak zegar test§c (podgląd 1 min, 2.5×) §f| §e/stojak zegar anuluj§c §f| §e/stojak zegar tryb <normalny|zamiatajacy>§c.");
                return true;
            }
            if (args[1].equalsIgnoreCase("test") || args[1].equalsIgnoreCase("start")
                    || args[1].equalsIgnoreCase("podglad") || args[1].equalsIgnoreCase("podgląd")) {
                plugin.getClockManager().startTest(player);
                return true;
            }
            if (args[1].equalsIgnoreCase("anuluj") || args[1].equalsIgnoreCase("stop")
                    || args[1].equalsIgnoreCase("koniec") || args[1].equalsIgnoreCase("wylacz")
                    || args[1].equalsIgnoreCase("wyłącz")) {
                plugin.getClockManager().cancelTest(player);
                return true;
            }
            // /stojak zegar tryb <normalny|zamiatajacy> - styl obrotu wskazówek
            if (args[1].equalsIgnoreCase("tryb") || args[1].equalsIgnoreCase("mode")) {
                if (args.length < 3) {
                    AntiSpam.send(player, "§cUżyj: §e/stojak zegar tryb normalny§c (pełne koło, prawdziwy czas) albo §e/stojak zegar tryb zamiatajacy§c (pełne koło z odświeżeniem na każdej ćwiartce 90°).");
                    return true;
                }
                String mode = args[2].toLowerCase();
                if (mode.startsWith("normaln") || mode.equals("normal") || mode.equals("pelny")
                        || mode.equals("pełny") || mode.equals("0") || mode.equals("false")
                        || mode.equals("off") || mode.equals("wylacz") || mode.equals("wyłącz")) {
                    plugin.getClockManager().setSweep(player, false);
                } else if (mode.startsWith("zamiataj") || mode.equals("sweep") || mode.equals("1")
                        || mode.equals("true") || mode.equals("on")) {
                    plugin.getClockManager().setSweep(player, true);
                } else {
                    AntiSpam.send(player, "§cNieznany tryb: " + args[2]
                            + "§c. Użyj: §e/stojak zegar tryb normalny§c albo §e/stojak zegar tryb zamiatajacy§c.");
                }
                return true;
            }
            AntiSpam.send(player, "§cNieznana opcja. Użyj: §e/stojak zegar test§c §f| §e/stojak zegar anuluj§c §f| §e/stojak zegar tryb <normalny|zamiatajacy>§c.");
            return true;
        }
        // Przyciąganie (snap) dla move/rotate: /stojak snap [on|off]
        if (sub.equals("snap")) {
            boolean on;
            if (args.length >= 2) {
                switch (args[1].toLowerCase()) {
                    case "on", "wlacz", "włącz", "tak", "1", "true" -> on = true;
                    case "off", "wylacz", "wyłącz", "nie", "0", "false" -> on = false;
                    default -> {
                        AntiSpam.send(player, "§cUżyj: /stojak snap [on|off]");
                        return true;
                    }
                }
            } else {
                on = !plugin.getArmorStandTuner().isSnap(player); // bez argumentu przełącz
            }
            plugin.getArmorStandTuner().setSnap(player, on);
            if (on) {
                AntiSpam.send(player, "§aWłączono przyciąganie (snap): §fmove §7do 0.25 bloka, §frotate §7do 5°.");
            } else {
                AntiSpam.send(player, "§7Wyłączono przyciąganie (snap).");
            }
            return true;
        }
        // Sprzątanie osieroconych części po usuniętych stojakach: /stojak sprzataj [promień]
        if (sub.equals("sprzataj") || sub.equals("posprzataj") || sub.equals("czysc") || sub.equals("cleanup")) {
            double radius = 10;
            if (args.length >= 2) {
                Double r = parseDoubleOr(args[1], null);
                if (r == null || r < 1 || r > 50) {
                    AntiSpam.send(player, "§cUżyj: /stojak sprzataj [promień 1-50]");
                    return true;
                }
                radius = r;
            }
            int removed = plugin.getArmorStandTuner().cleanupOrphans(player, radius);
            if (removed > 0) {
                AntiSpam.send(player, "§aPosprzątano " + removed + " osieroconych "
                        + (removed == 1 ? "część" : "części") + " (bloków/przedmiotów bez stojaka).");
            } else {
                AntiSpam.send(player, "§7Brak osieroconych części w promieniu " + (int) radius + " bloków.");
            }
            return true;
        }
        // Flaga dekoracji mapy: /stojak dekoracja on|off - oznacza stojak jako dekorację,
        // której NIE usuwa sprzątanie po zakończeniu areny (i nie strzela, nie ma paska HP).
        // Działa TYLKO na stojaku, na który patrzysz (bez fallbacku do aktywnego). Flaga
        // zapisuje się w PDC, przetrwa restart i trafia do pliku presetu (/stojak save).
        if (sub.equals("dekoracja") || sub.equals("dekoracje")) {
            ArmorStandTuner tuner = plugin.getArmorStandTuner();
            ArmorStand stand = tuner.findLookedStand(player, true);
            if (stand == null) {
                stand = tuner.findLookedStand(player, false);
                if (stand != null) tuner.bind(stand);
            }
            if (stand == null) {
                AntiSpam.send(player, "§cNie patrzysz na żaden stojak.");
                return true;
            }
            boolean on;
            if (args.length < 2) {
                on = !plugin.getArmorStandTuner().isDecoration(stand); // przełącz
            } else if (args[1].equalsIgnoreCase("on")
                    || args[1].equalsIgnoreCase("tak") || args[1].equalsIgnoreCase("1")) {
                on = true;
            } else if (args[1].equalsIgnoreCase("off")
                    || args[1].equalsIgnoreCase("nie") || args[1].equalsIgnoreCase("0")) {
                on = false;
            } else {
                AntiSpam.send(player, "§cUżyj: /stojak dekoracja on|off (albo bez argumentu = przełącz)");
                return true;
            }
            plugin.getArmorStandTuner().setDecoration(stand, on);
            AntiSpam.send(player, on
                    ? "§aStojak oznaczony jako dekoracja mapy"
                    : "§7Stojak nie jest już dekoracją");
            return true;
        }

        AntiSpam.send(player, "§cNieznana podkomenda: §e" + sub
                + "§c. §e/stojak help§c po listę; na stojaku po nazwie (bez patrzenia): "
                + "§e/stojak <nazwa> duplikuj czesc <liczba>§c.");
        return true;
    }

    /** Wspólna obsługa /stojak [<materiał>|czesc <liczba>] move <x> <y> <z>. */
    private boolean handleMove(Player player, String materialName, Integer partNumber, String sx, String sy, String sz) {
        Double x = parseDoubleOr(sx, null);
        Double y = parseDoubleOr(sy, null);
        Double z = parseDoubleOr(sz, null);
        if (x == null || y == null || z == null) {
            AntiSpam.send(player, "§cUżyj: /stojak move ([część_liczba]) <x y z>");
            return true;
        }
        if (Math.abs(x) > 50 || Math.abs(y) > 50 || Math.abs(z) > 50) {
            AntiSpam.send(player, "§cTo wygląda na współrzędne świata, a nie przesunięcie (delta) - część poleciałaby "
                    + "ponad 50 bloków. Żeby ustawić część na blok, na który patrzysz: §e/stojak tam§c.");
            return true;
        }
        ArmorStand stand = tunedStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie patrzysz na żaden stojak.");
            return true;
        }
        ArmorStandTuner tuner = plugin.getArmorStandTuner();
        // /stojak move <materiał>_<liczba> ... - wybierz KONKRETNĄ część z materiału
        if (materialName != null && partNumber == null) {
            MatNum mn = parseMaterialNumber(materialName);
            if (mn != null) {
                materialName = mn.matName;
                partNumber = mn.number;
            }
        }
        if (partNumber != null) {
            Material m = materialName != null ? Material.matchMaterial(materialName) : null;
            if (materialName != null && m == null) {
                AntiSpam.send(player, "§cNieznany materiał: " + materialName);
                return true;
            }
            if (!tuner.selectPartNumber(stand, partNumber, m)) {
                int total = tuner.getParts(stand).size();
                if (m != null) {
                    AntiSpam.send(player, "§cBrak części nr §e" + partNumber + "§c z materiału §e"
                            + materialName + "§c (ten stojak ma " + total + " "
                            + (total == 1 ? "część" : "części") + ").");
                } else {
                    AntiSpam.send(player, "§cTen stojak ma " + total + " "
                            + (total == 1 ? "część" : "części")
                            + " - nie ma części nr " + partNumber + ".");
                }
                return true;
            }
        } else if (!selectPartForEdit(player, materialName, tuner, stand)) {
            return true;
        }
        tuner.moveSelectedPart(stand, player, x, y, z);
        return true;
    }

    /** Rozbija „<materiał>_<liczba>" (np. stone_2) na materiał i numer części. */
    private record MatNum(String matName, int number) {
    }

    private MatNum parseMaterialNumber(String arg) {
        int us = arg.lastIndexOf('_');
        if (us <= 0 || us == arg.length() - 1) return null;
        Integer n = parseIntOr(arg.substring(us + 1), null);
        if (n == null || n < 1) return null;
        String matName = arg.substring(0, us);
        if (Material.matchMaterial(matName) == null) return null;
        return new MatNum(matName, n);
    }

    /** Podpowiedzi dla /stojak move <Tab>: „czesc" + materiały części; gdy materiał
     *  występuje kilka razy - też <materiał>_<liczba> (np. stone_1, stone_2), żeby
     *  dało się wybrać KONKRETNĄ część, a nie losową z danego typu. */
    private List<String> movePartSuggestions(Player p) {
        ArmorStandTuner tuner = plugin.getArmorStandTuner();
        ArmorStand stand = tuner.findLookedStand(p, false);
        if (stand == null) stand = tuner.getActive(p);
        List<String> out = new ArrayList<>(List.of("czesc", "część", "part"));
        if (stand == null) return out;
        Map<String, Integer> counts = new HashMap<>();
        for (DisplayPart part : tuner.getParts(stand)) {
            counts.merge(part.material().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        counts.forEach((mat, cnt) -> {
            if (cnt <= 1) {
                out.add(mat);
            } else {
                for (int i = 1; i <= cnt; i++) out.add(mat + "_" + i);
            }
        });
        return out;
    }

    /** Wspólna obsługa /stojak [<materiał>] scale [<wartość>|<x> <y> <z>|reset]. */
    private boolean handleScale(Player player, String materialName, String[] args, int start) {
        ArmorStandTuner tuner = plugin.getArmorStandTuner();
        ArmorStand stand = tunedStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie patrzysz na żaden stojak.");
            return true;
        }
        // /stojak scale all <współczynnik|reset> - skala WSZYSTKICH części naraz
        if (materialName == null && args.length >= start + 1 && args[start].equalsIgnoreCase("all")) {
            if (args.length < start + 2) {
                AntiSpam.send(player, "§cUżyj: /stojak scale all <współczynnik> (np. 0.8) | /stojak scale all reset");
                return true;
            }
            if (args[start + 1].equalsIgnoreCase("reset")) {
                tuner.resetAllPartsScale(stand, player);
                return true;
            }
            Double v = parseDoubleOr(args[start + 1], null);
            if (v == null || v <= 0) {
                AntiSpam.send(player, "§cUżyj: /stojak scale all <współczynnik> (np. 0.8) | /stojak scale all reset");
                return true;
            }
            tuner.scaleAllParts(stand, player, v);
            return true;
        }
        if (!selectPartForEdit(player, materialName, tuner, stand)) return true;

        if (args.length == start + 1 && args[start].equalsIgnoreCase("reset")) {
            tuner.scaleSelectedPart(stand, player, 1, 1, 1);
            return true;
        }
        if (args.length == start + 1) {
            Double v = parseDoubleOr(args[start], null);
            if (v == null) {
                AntiSpam.send(player, "§cUżyj: /stojak scale <wartość> | /stojak scale <x> <y> <z> | /stojak scale reset");
                return true;
            }
            tuner.scaleSelectedPart(stand, player, v, v, v);
            return true;
        }
        if (args.length == start + 3) {
            Double x = parseDoubleOr(args[start], null);
            Double y = parseDoubleOr(args[start + 1], null);
            Double z = parseDoubleOr(args[start + 2], null);
            if (x == null || y == null || z == null) {
                AntiSpam.send(player, "§cUżyj: /stojak scale <x> <y> <z>");
                return true;
            }
            tuner.scaleSelectedPart(stand, player, x, y, z);
            return true;
        }
        AntiSpam.send(player, "§cUżyj: /stojak scale <wartość> | /stojak scale <x> <y> <z> | /stojak scale reset");
        return true;
    }

    /**
     * Obsługa /stojak [<materiał>] rotate [<x> <y> <z>|reset].
     * Bez materiału obraca CAŁY stojak (korpus + wszystkie doczepione części -
     * pozycje części krążą wokół stojaka, a yaw/pitch stojaka się zmienia).
     * Z materiałem (/stojak <materiał> rotate ...) obraca wybraną część jak dawniej.
     */
    private boolean handleRotate(Player player, String materialName, String[] args, int start) {
        ArmorStandTuner tuner = plugin.getArmorStandTuner();
        ArmorStand stand = tunedStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie patrzysz na żaden stojak.");
            return true;
        }
        // bez materiału: obrót CAŁEGO stojaka (nie pojedynczej części)
        if (materialName == null) {
            if (args.length == start + 1 && args[start].equalsIgnoreCase("reset")) {
                tuner.rotateStand(stand, player, 0, 0, 0, true);
                return true;
            }
            if (args.length == start + 1) {
                Double v = parseDoubleOr(args[start], null);
                if (v == null) {
                    AntiSpam.send(player, "§cUżyj: /stojak rotate <x> <y> <z> (stopnie) | /stojak rotate reset");
                    return true;
                }
                tuner.rotateStand(stand, player, 0, v, 0, false); // pojedyncza liczba = obrót wokół Y
                return true;
            }
            if (args.length == start + 3) {
                Double x = parseDoubleOr(args[start], null);
                Double y = parseDoubleOr(args[start + 1], null);
                Double z = parseDoubleOr(args[start + 2], null);
                if (x == null || y == null || z == null) {
                    AntiSpam.send(player, "§cUżyj: /stojak rotate <x> <y> <z> (stopnie)");
                    return true;
                }
                tuner.rotateStand(stand, player, x, y, z, false);
                return true;
            }
            AntiSpam.send(player, "§cUżyj: /stojak rotate <x> <y> <z> (stopnie) | /stojak rotate reset");
            return true;
        }
        // z materiałem: obrót konkretnej części
        if (!selectPartForEdit(player, materialName, tuner, stand)) return true;

        if (args.length == start + 1 && args[start].equalsIgnoreCase("reset")) {
            tuner.rotateSelectedPart(stand, player, 0, 0, 0, true);
            return true;
        }
        if (args.length == start + 1) {
            Double v = parseDoubleOr(args[start], null);
            if (v == null) {
                AntiSpam.send(player, "§cUżyj: /stojak rotate <x> <y> <z> (stopnie) | /stojak rotate reset");
                return true;
            }
            tuner.rotateSelectedPart(stand, player, 0, v, 0, false); // pojedyncza liczba = obrót wokół Y
            return true;
        }
        if (args.length == start + 3) {
            Double x = parseDoubleOr(args[start], null);
            Double y = parseDoubleOr(args[start + 1], null);
            Double z = parseDoubleOr(args[start + 2], null);
            if (x == null || y == null || z == null) {
                AntiSpam.send(player, "§cUżyj: /stojak rotate <x> <y> <z> (stopnie)");
                return true;
            }
            tuner.rotateSelectedPart(stand, player, x, y, z, false);
            return true;
        }
        AntiSpam.send(player, "§cUżyj: /stojak rotate <x> <y> <z> (stopnie) | /stojak rotate reset");
        return true;
    }

    /**
     * Dla /stojak <materiał|nazwa> move|scale|rotate: gdy pierwszy argument to NIE
     * materiał, ale istnieje stojak o takiej nazwie - dołącz go (bez patrzenia) i zwróć
     * null (operacja na wybranej części tego stojaka). Materiał ma pierwszeństwo
     * (stojak nazwany jak materiał = materiał). Gdy ani materiał, ani stojak - zwraca
     * argument, a dalej poleci normalny błąd "Nieznany materiał".
     */
    private String resolveMaterialOrNamed(Player player, String arg) {
        if (Material.matchMaterial(arg) != null) return arg;
        ArmorStand named = plugin.getArmorStandTuner().findNamedStand(player, arg);
        if (named != null) {
            plugin.getArmorStandTuner().bind(named);
            plugin.getArmorStandTuner().setActive(player, named);
            AntiSpam.sendActionBar(player, "§7Stojak o nazwie §e" + arg
                    + "§7 - edytujesz go bez patrzenia (§e/stojak aktywny§7 - pozycja).");
            return null;
        }
        return arg;
    }

    /** Wybiera część po materiale (najpierw ta, na którą patrzysz) albo sprawdza wybraną. */
    private boolean selectPartForEdit(Player player, String materialName, ArmorStandTuner tuner, ArmorStand stand) {
        if (materialName != null) {
            Material m = Material.matchMaterial(materialName);
            if (m == null) {
                AntiSpam.send(player, "§cNieznany materiał: " + materialName);
                return false;
            }
            if (!tuner.selectPartForOperation(player, stand, materialName)) {
                AntiSpam.send(player, "§cTen stojak nie ma części z materiału: " + materialName);
                return false;
            }
        } else if (!tuner.hasSelectedPart(stand)) {
            AntiSpam.send(player, "§cNie wybrano części. Kliknij stojak STICKiem, żeby wybrać część, albo podaj materiał, np. /stojak stone scale 0.5");
            return false;
        }
        return true;
    }

    /**
     * Znajduje stojak do strojenia: edytowalny, a jeśli go nie ma (np. postawiony
     * przed restartem, gdy status "strojony" zniknął z pamięci) - automatycznie
     * dołącza go (/stojak bind) i odtwarza śledzenie doczepionych części.
     */
    private ArmorStand tunedStand(Player player) {
        ArmorStandTuner tuner = plugin.getArmorStandTuner();
        ArmorStand stand = tuner.findLookedStand(player, true);
        if (stand == null) {
            stand = tuner.findLookedStand(player, false);
            if (stand != null) tuner.bind(stand);
        }
        if (stand == null) {
            stand = tuner.getActive(player);
            if (stand != null) {
                if (!tuner.isEditable(stand)) tuner.bind(stand);
                AntiSpam.sendActionBar(player, "§7Nie patrzysz na żaden stojak - edytujesz AKTYWNY stojak (§e/stojak aktywny§7 - pozycja).");
            }
        }
        if (stand != null) tuner.setActive(player, stand);
        return stand;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private Double parseDoubleOr(String raw, Double fallback) {
        if (raw == null) return fallback;
        try {
            return Double.parseDouble(raw.replace(',', '.')); // akceptuj 0,5 zamiast 0.5
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Integer parseIntOr(String raw, Integer fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double sinkParam(String[] args) {
        for (String a : args) {
            if (a.startsWith("sink:")) {
                try {
                    return Double.parseDouble(a.substring(5).replace(',', '.')); // akceptuj 0,5
                } catch (NumberFormatException ignored) {
                }
            }
        }
        // domyślnie stojak staje NA WIERZCHU patrzonego bloku (jak wieżyczka);
        // sink:0 = 1 blok niżej, sink:1 = 2 bloki niżej itd.
        return -1.0;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // tab-complete nigdy nie może wywalić graczowi "an internal error occurred..." -
        // każdy błąd łapiemy, logujemy do konsoli serwera i wracamy pustą listą podpowiedzi
        try {
            return tabCompleteSafe(sender, command, alias, args);
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd tab-complete /stojak: " + e);
            return List.of();
        }
    }

    private List<String> tabCompleteSafe(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return List.of();
        String first = args[0].toLowerCase();

        // Podpowiedzi współrzędnych bloku, na który patrzy gracz (Y = wierzch bloku).
        // Światowe koordynaty mają sens tylko dla paste/offset/stworz/place -
        // dla move/scale/rotate NIE (move traktuje wartości jako deltę przesunięcia!),
        // a dla part są to OFFSETY względem stojaka (patrz niżej).
        String[] coords = targetCoords(sender);
        if (coords != null) {
            // x y z od indeksu 1: paste, offset, stworz
            if (first.equals("paste") || first.equals("offset") || first.equals("stworz")) {
                if (args.length == 2) return filter(coords[0], args[1]);
                if (args.length == 3) return filter(coords[1], args[2]);
                if (args.length == 4) return filter(coords[2], args[3]);
            }
            // x y z od indeksu 2: place <nazwa> (współrzędne świata - absolutne)
            if (first.equals("place") && args.length >= 3) {
                if (args.length == 3) return filter(coords[0], args[2]);
                if (args.length == 4) return filter(coords[1], args[3]);
                if (args.length == 5) return filter(coords[2], args[4]);
            }
        }

        // ===== podpowiedzi argumentów pozostałych podkomend: on/off, koordynaty, liczby =====
        // /stojak help <Tab> - numery stron
        if (first.equals("help") && args.length == 2) {
            int pages = plugin.getArmorStandTuner().helpPages();
            List<String> out = new ArrayList<>();
            for (int i = 1; i <= pages; i++) out.add(String.valueOf(i));
            return out;
        }
        // /stojak visible|niewidoczny|snap|nazwa|dekoracja <Tab> - on/off
        if ((first.equals("visible") || first.equals("niewidoczny") || first.equals("niewidzialny")
                || first.equals("snap")
                || first.equals("nazwa") || first.equals("name")
                || first.equals("dekoracja") || first.equals("dekoracje"))
                && args.length == 2) {
            return List.of("on", "off");
        }
        // /stojak krzeslo [<nazwa>] <Tab> - nazwa patrzonego stojaka (jeśli ma) albo on/off
        if ((first.equals("krzeslo") || first.equals("chair")) && args.length == 2) {
            List<String> out = new ArrayList<>(List.of("on", "off"));
            if (sender instanceof Player p) {
                ArmorStand stand = tunedStand(p);
                if (stand != null && stand.getCustomName() != null && !stand.getCustomName().isEmpty()) {
                    out.add(0, stand.getCustomName());
                }
            }
            return out;
        }
        // /stojak lustro <Tab> - czesc (kopia części) albo oś x/z (cały stojak)
        if ((first.equals("lustro") || first.equals("mirror")) && args.length == 2) {
            return List.of("czesc", "x", "z");
        }
        // /stojak lustro czesc <Tab> - oś x/z
        if ((first.equals("lustro") || first.equals("mirror")) && args.length == 3
                && (args[1].equalsIgnoreCase("czesc") || args[1].equalsIgnoreCase("część")
                || args[1].equalsIgnoreCase("part"))) {
            return List.of("x", "z");
        }
        // /stojak odbicie|odbij|... <Tab> (aliasy lustro czesc) - oś x/z
        if ((first.equals("odbicie") || first.equals("odbij") || first.equals("mirrorpart")
                || first.equals("lustroczesc") || first.equals("lustro_czesc")) && args.length == 2) {
            return List.of("x", "z");
        }
        // /stojak glow <Tab> - off / sekundy
        if (first.equals("glow") && args.length == 2) {
            return List.of("off", "5", "10", "30", "60");
        }
        // /stojak sprzataj <Tab> - promienie
        if ((first.equals("sprzataj") || first.equals("posprzataj") || first.equals("czysc")
                || first.equals("cleanup")) && args.length == 2) {
            return List.of("5", "10", "20", "30", "50");
        }
        // /stojak liczba [promień] <Tab> - promienie
        if ((first.equals("liczba") || first.equals("count") || first.equals("ile")
                || first.equals("ilosc") || first.equals("ilość")) && args.length == 2) {
            return List.of("50", "100", "200", "500");
        }
        // /stojak nazwa <Tab> - off / pokaz / ukryj (dowolny tekst też działa)
        if ((first.equals("nazwa") || first.equals("name")) && args.length == 2) {
            return List.of("off", "pokaz", "ukryj");
        }
        // /stojak tam <Tab> - materiały obecne na stojaku
        if (first.equals("tam") && args.length == 2 && sender instanceof Player p) {
            return plugin.getArmorStandTuner().partMaterials(p);
        }
        // /stojak offset <Tab> - koordynata X bloku pod celownikiem albo reset
        if (first.equals("offset") && args.length == 2) {
            List<String> out = new ArrayList<>();
            if (coords != null) out.add(coords[0]);
            out.add("reset");
            return out;
        }
        // /stojak scale <Tab> - all / reset / przykładowe wartości; scale all <Tab> - reset / wartości
        if (first.equals("scale")) {
            if (args.length == 2) return List.of("all", "reset", "0.5", "0.75", "1.5", "2.0");
            if (args.length == 3 && args[1].equalsIgnoreCase("all")) {
                return List.of("reset", "0.5", "0.75", "0.8", "1.5", "2.0");
            }
        }
        // /stojak rotate <Tab> - reset / przykładowe kąty
        if (first.equals("rotate") && args.length == 2) {
            return List.of("reset", "45", "90", "180");
        }
        // /stojak funkcja siedzenie [x y z] - koordynaty bloku pod celownikiem
        if (first.equals("funkcja") && args.length >= 3 && (args[1].equalsIgnoreCase("siedzenie")
                || args[1].equalsIgnoreCase("krzeslo") || args[1].equalsIgnoreCase("chair"))) {
            if (coords != null) {
                if (args.length == 3) return filter(coords[0], args[2]);
                if (args.length == 4) return filter(coords[1], args[3]);
                if (args.length == 5) return filter(coords[2], args[4]);
            }
        }
        // /stojak move <Tab> (puste albo nie-liczba): delta na blok pod celownikiem LUB
        // „czesc" LUB materiał obecny na stojaku (a przy kilku częściach tego samego
        // materiału też <materiał>_<liczba>, np. stone_2) - pierwszy argument move
        // może być jednym i drugim
        if (first.equals("move") && args.length == 2 && parseDoubleOr(args[1], null) == null
                && sender instanceof Player p) {
            String[] d = plugin.getArmorStandTuner().moveDeltaToLookedBlockText(p, null);
            List<String> out = new ArrayList<>();
            if (d != null) out.add(d[0]);
            for (String s : movePartSuggestions(p)) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
            }
            return out;
        }

        // part <blok|przedmiot> [x y z]: podpowiadaj materiał (blok/przedmiot) na 2.
        // argumencie; x y z to NIE są współrzędne świata, tylko OFFSET względem stojaka -
        // podpowiadaj offset od stojaka do wierzchu bloku pod celownikiem (żeby nie
        // podpowiadać światowych Y, przez co część lądowała kilkadziesiąt bloków nad
        // stojakiem).
        if (first.equals("part") && sender instanceof Player p) {
            if (args.length == 2) {
                List<String> out = materialSuggestions(args[1]);
                if ("remove".startsWith(args[1].toLowerCase())) out.add(0, "remove");
                return out;
            }
            String[] off = plugin.getArmorStandTuner().partOffsetToLookedBlockText(p);
            if (off != null && args.length >= 3) {
                if (args.length == 3) return filter(off[0], args[2]);
                if (args.length == 4) return filter(off[1], args[3]);
                if (args.length == 5) return filter(off[2], args[4]);
            }
        }
        // move: podpowiadaj DELTĘ, która ustawi część na bloku pod celownikiem
        // (nie współrzędne świata - te powodowały „lot” części o 60+ bloków w górę).
        if (sender instanceof Player player && args.length > 0) {
            ArmorStandTuner tuner = plugin.getArmorStandTuner();
            String[] d = null;
            int coordIdx = -1;
            if (args.length >= 2 && args[1].equalsIgnoreCase("move")) {
                // /stojak <materiał> move <x> <y> <z> (materiał może być <materiał>_<liczba>)
                MatNum mn = parseMaterialNumber(args[0]);
                d = tuner.moveDeltaToLookedBlockText(player, mn != null ? mn.matName : args[0]);
                coordIdx = args.length - 3;
            } else if (first.equals("move") && args.length >= 2) {
                if (args[1].equalsIgnoreCase("czesc") || args[1].equalsIgnoreCase("część")
                        || args[1].equalsIgnoreCase("part")) {
                    if (args.length == 3) {
                        // /stojak move czesc <Tab> - numery części (1..N z action bara)
                        ArmorStand stand = tuner.findLookedStand(player, false);
                        if (stand == null) stand = tuner.getActive(player);
                        if (stand == null) return List.of();
                        int n = tuner.getParts(stand).size();
                        List<String> nums = new ArrayList<>();
                        for (int i = 1; i <= n; i++) {
                            if (String.valueOf(i).startsWith(args[2])) nums.add(String.valueOf(i));
                        }
                        return nums;
                    }
                    if (args.length >= 4) {
                        // /stojak move czesc <liczba> <x|y|z> - delta pod blok pod celownikiem
                        Integer pn = parseIntOr(args[2], null);
                        d = (pn != null && pn >= 1) ? tuner.moveDeltaToLookedBlockText(player, pn) : null;
                        coordIdx = args.length - 4;
                    }
                } else if (args.length == 2) {
                    d = tuner.moveDeltaToLookedBlockText(player, null);
                    coordIdx = 0;
                } else if (parseDoubleOr(args[1], null) != null) {
                    // /stojak move <x> <y> <z>
                    d = tuner.moveDeltaToLookedBlockText(player, null);
                    coordIdx = args.length - 2;
                } else if (args.length >= 3 && args.length <= 5) {
                    // /stojak move <materiał> <x> <y> <z> (materiał może być <materiał>_<liczba>)
                    MatNum mn = parseMaterialNumber(args[1]);
                    d = tuner.moveDeltaToLookedBlockText(player, mn != null ? mn.matName : args[1]);
                    coordIdx = args.length - 3;
                }
            }
            if (d != null && coordIdx >= 0 && coordIdx < 3) {
                return filter(d[coordIdx], args[args.length - 1]);
            }
        }

        // duplikuj: podpowiadaj "czesc" LUB materiał obecny na stojaku, a potem numer
        // części (1..N) - liczba = numer z action bara przy przewijaniu STICKiem
        if (first.equals("duplikuj") && sender instanceof Player p) {
            if (args.length == 2) {
                List<String> out = new ArrayList<>();
                for (String o : List.of("czesc", "część", "part")) {
                    if (o.startsWith(args[1].toLowerCase())) out.add(o);
                }
                for (String m : plugin.getArmorStandTuner().partMaterials(p)) {
                    if (m.startsWith(args[1].toLowerCase())) out.add(m);
                }
                return out;
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("czesc")
                    || args[1].equalsIgnoreCase("część") || args[1].equalsIgnoreCase("part"))) {
                ArmorStand stand = plugin.getArmorStandTuner().findLookedStand(p, false);
                if (stand == null) stand = plugin.getArmorStandTuner().getActive(p);
                if (stand != null) {
                    int n = plugin.getArmorStandTuner().getParts(stand).size();
                    List<String> nums = new ArrayList<>();
                    for (int i = 1; i <= n; i++) {
                        if (String.valueOf(i).startsWith(args[2])) nums.add(String.valueOf(i));
                    }
                    return nums;
                }
            }
        }

        // /stojak <nazwa stojaka> ... - podpowiedzi dla operacji na stojaku po nazwie
        // (bez patrzenia): najpierw operacje, potem "czesc", potem numery części tego stojaka
        if (sender instanceof Player p && args.length >= 2 && !KNOWN_SUBS.contains(first)) {
            ArmorStand named = plugin.getArmorStandTuner().findNamedStand(p, args[0]);
            if (named != null) {
                if (args.length == 2) {
                    // celowo bez "remove": /stojak <nazwa> remove nie istnieje -
                    // usuwanie działa tylko na patrzonego stojaka, a usuwanie
                    // zapisów po nazwie (/stojak delete) jest wyłączone
                    List<String> ops = List.of("duplikuj", "stats", "informacja", "part", "usun", "move",
                            "scale", "rotate", "partclear", "undo", "save", "bind");
                    List<String> out = new ArrayList<>();
                    for (String o : ops) {
                        if (o.startsWith(args[1].toLowerCase())) out.add(o);
                    }
                    return out;
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("duplikuj")) {
                    List<String> out = new ArrayList<>();
                    for (String o : List.of("czesc", "część", "part")) {
                        if (o.startsWith(args[2].toLowerCase())) out.add(o);
                    }
                    return out;
                }
                if (args.length == 4 && args[1].equalsIgnoreCase("duplikuj")
                        && (args[2].equalsIgnoreCase("czesc") || args[2].equalsIgnoreCase("część")
                        || args[2].equalsIgnoreCase("part"))) {
                    int n = plugin.getArmorStandTuner().getParts(named).size();
                    List<String> nums = new ArrayList<>();
                    for (int i = 1; i <= n; i++) {
                        if (String.valueOf(i).startsWith(args[3])) nums.add(String.valueOf(i));
                    }
                    return nums;
                }
            }
        }

        // /stojak usun czesc <Tab> - numery części 1..N stojaka (patrzonego albo AKTYWNEGO)
        if (args.length == 3 && sender instanceof Player p && (args[0].equalsIgnoreCase("usun")
                || args[0].equalsIgnoreCase("usunczesc") || args[0].equalsIgnoreCase("removepart"))
                && (args[1].equalsIgnoreCase("czesc") || args[1].equalsIgnoreCase("część")
                || args[1].equalsIgnoreCase("part"))) {
            ArmorStandTuner tuner = plugin.getArmorStandTuner();
            ArmorStand st = tuner.findLookedStand(p, false);
            if (st == null) st = tuner.getActive(p);
            if (st != null) {
                int n = tuner.getParts(st).size();
                List<String> nums = new ArrayList<>();
                for (int i = 1; i <= n; i++) {
                    if (String.valueOf(i).startsWith(args[2])) nums.add(String.valueOf(i));
                }
                return nums;
            }
        }
        // /stojak usun [all [czesc]] <Tab> - materiały obecne na stojaku (nigdy nie czytamy
        // args[1] bez sprawdzenia args.length - inaczej tab po "usun" wywalałby wyjątek
        // i nie pokazywał żadnych podpowiedzi)
        if (sender instanceof Player p && (args[0].equalsIgnoreCase("usun")
                || args[0].equalsIgnoreCase("usunczesc") || args[0].equalsIgnoreCase("removepart"))) {
            boolean all = args.length >= 2 && (args[1].equalsIgnoreCase("all")
                    || args[1].equalsIgnoreCase("wszystkie") || args[1].equalsIgnoreCase("wszystko"));
            if (all) {
                // /stojak usun all <Tab> - "czesc" + materiały obecne na stojaku
                if (args.length == 2) {
                    List<String> out = new ArrayList<>();
                    out.add("czesc");
                    out.add("część");
                    out.add("part");
                    out.addAll(plugin.getArmorStandTuner().partMaterials(p));
                    return out;
                }
                // /stojak usun all czesc <Tab> - wszystkie materiały ze stojaka (bez prefiksu)
                if (args.length == 3 && (args[2].equalsIgnoreCase("czesc")
                        || args[2].equalsIgnoreCase("część") || args[2].equalsIgnoreCase("part"))) {
                    return plugin.getArmorStandTuner().partMaterials(p);
                }
                // /stojak usun all <materiał>... / all czesc <materiał>... - filtr po prefiksie
                if (args.length == 3 || args.length == 4) {
                    String partial = args[args.length - 1].toLowerCase();
                    List<String> out = new ArrayList<>();
                    for (String m : plugin.getArmorStandTuner().partMaterials(p)) {
                        if (m.startsWith(partial)) out.add(m);
                    }
                    return out;
                }
            }
            // /stojak usun <Tab> (albo /stojak usun <prefiks><Tab>, albo usun czesc
            // <liczba> <Tab>) - materiały obecne na stojaku (zamiast przypadkowych
            // parametrów head:/hand:/... jak wcześniej)
            boolean czescNum = args.length == 4 && (args[1].equalsIgnoreCase("czesc")
                    || args[1].equalsIgnoreCase("część") || args[1].equalsIgnoreCase("part"));
            if (args.length == 1 || (args.length == 2 && !(args[1].equalsIgnoreCase("czesc")
                    || args[1].equalsIgnoreCase("część") || args[1].equalsIgnoreCase("part")))
                    || czescNum) {
                String partial = args.length == 2 ? args[1].toLowerCase()
                        : (czescNum ? args[3].toLowerCase() : "");
                List<String> out = new ArrayList<>();
                for (String m : plugin.getArmorStandTuner().partMaterials(p)) {
                    if (m.startsWith(partial)) out.add(m);
                }
                return out;
            }
        }
        // /stojak stats|informacja <Tab> - materiały obecne na stojaku
        if ((first.equals("stats") || first.equals("informacja") || first.equals("informacje"))
                && args.length == 2 && sender instanceof Player p) {
            List<String> out = new ArrayList<>();
            for (String m : plugin.getArmorStandTuner().partMaterials(p)) {
                if (m.startsWith(args[1].toLowerCase())) out.add(m);
            }
            return out;
        }
        // /stojak autosave [place] <Tab> - "place" i nazwy z pliku autosave
        if (first.equals("autosave") && sender instanceof Player p) {
            if (args.length == 2) {
                if ("place".startsWith(args[1].toLowerCase())) return List.of("place");
                return List.of();
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("place")) {
                List<String> out = new ArrayList<>();
                for (StandPreset pr : plugin.getStandPresetManager().readAutosave()) {
                    if (pr.name.toLowerCase().startsWith(args[2].toLowerCase())) out.add(pr.name);
                }
                return out;
            }
        }
        // /stojak zamien <Tab> - tylko „część";
        // /stojak zamien czesc <Tab> - numer części (1..N) albo stary materiał;
        // /stojak zamien ... <nowy> <Tab> - materiały na nowy materiał
        if (first.equals("zamien") && sender instanceof Player p) {
            ArmorStandTuner tuner = plugin.getArmorStandTuner();
            if (args.length == 2) {
                String a = args[1].toLowerCase(Locale.ROOT);
                if ("część".startsWith(a)) return List.of("część");
                return List.of();
            }
            boolean czescMode = args[1].equalsIgnoreCase("czesc")
                    || args[1].equalsIgnoreCase("część") || args[1].equalsIgnoreCase("cześć")
                    || args[1].equalsIgnoreCase("part");
            if (czescMode && args.length == 3) {
                // numer części (1..N) albo stary materiał
                List<String> out = new ArrayList<>();
                ArmorStand stand = tuner.findLookedStand(p, false);
                if (stand == null) stand = tuner.getActive(p);
                if (stand != null) {
                    int n = tuner.getParts(stand).size();
                    for (int i = 1; i <= n; i++) {
                        if (String.valueOf(i).startsWith(args[2])) out.add(String.valueOf(i));
                    }
                }
                for (String m : tuner.partMaterials(p)) {
                    if (m.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(m);
                }
                return out;
            }
            if (args.length == 3 || args.length == 4) {
                // nowy materiał (args.length == 3 = /stojak zamien <materiał_N> <nowy>)
                return materialSuggestions(args[args.length - 1]);
            }
        }
        // /stojak head|hand|offhand <Tab> - materiał na wyposażenie albo brak/air
        if ((first.equals("head") || first.equals("hand") || first.equals("offhand"))
                && args.length == 2 && sender instanceof Player p) {
            List<String> out = materialSuggestions(args[1]);
            out.add(0, "brak");
            return out;
        }

        List<String> options = new ArrayList<>();
        // parametry tworzenia: /stojak stworz [x y z] <parametr:wartość>
        if (first.equals("stworz") && args.length >= 5) {
            return PARAMS;
        }
        if (args.length == 1) {
            options.add("stworz");
            options.add("aktywny");
            options.add("visible");
            options.add("head");
            options.add("hand");
            options.add("offhand");
            options.add("snap");
            options.add("sprzataj");
            options.add("dekoracja");
            options.add("zamien");
            options.add("lustro");
            options.add("nazwa");
            options.add("glow");
            options.add("help");
            options.add("offset");
            options.add("part");
            options.add("partclear");
            options.add("undo");
            options.add("ponow");
            options.add("save");
            options.add("saveitem");
            options.add("place");
            options.add("item");
            options.add("list");
            options.add("delete");
            options.add("bind");
            options.add("odzyskaj");
            options.add("znajdz");
            options.add("liczba");
            options.add("autosave");
            options.add("krzeslo");
            options.add("funkcja");
            options.add("zegar");
            options.add("remove");
            options.add("przywroc");
            options.add("restore");
            options.add("usun");
            options.add("duplikuj");
            options.add("stats");
            options.add("informacja");
            options.add("dajitemy");
            options.add("move");
            options.add("tam");
            options.add("scale");
            options.add("rotate");
            options.add("copy");
            options.add("paste");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("place")
                || args[0].equalsIgnoreCase("item") || args[0].equalsIgnoreCase("delete"))) {
            options.addAll(plugin.getStandPresetManager().listPresets());
        }
        // /stojak delete <nazwa> <Tab> - potwierdzenie usunięcia
        if (first.equals("delete") && args.length == 3) {
            if ("confirm".startsWith(args[2].toLowerCase())) return List.of("confirm");
        }
        // /stojak zegar <Tab> - opcje
        if (first.equals("zegar") && args.length == 2) {
            List<String> out = new ArrayList<>();
            if ("test".startsWith(args[1].toLowerCase())) out.add("test");
            if ("anuluj".startsWith(args[1].toLowerCase())) out.add("anuluj");
            if ("tryb".startsWith(args[1].toLowerCase())) out.add("tryb");
            return out;
        }
        // /stojak zegar tryb <Tab> - nazwy trybów
        if (first.equals("zegar") && args.length == 3
                && (args[1].equalsIgnoreCase("tryb") || args[1].equalsIgnoreCase("mode"))) {
            List<String> out = new ArrayList<>();
            if ("normalny".startsWith(args[2].toLowerCase())) out.add("normalny");
            if ("zamiatajacy".startsWith(args[2].toLowerCase())) out.add("zamiatajacy");
            return out;
        }
        // /stojak funkcja <Tab> - nazwy funkcji
        if (first.equals("funkcja") && args.length == 2) {
            List<String> out = new ArrayList<>();
            if ("siedzenie".startsWith(args[1].toLowerCase())) out.add("siedzenie");
            if ("tv".startsWith(args[1].toLowerCase())) out.add("tv");
            if ("telewizor".startsWith(args[1].toLowerCase())) out.add("telewizor");
            if ("ekran".startsWith(args[1].toLowerCase())) out.add("ekran");
            if ("lampa".startsWith(args[1].toLowerCase())) out.add("lampa");
            if ("latarnia".startsWith(args[1].toLowerCase())) out.add("latarnia");
            if ("zmiana".startsWith(args[1].toLowerCase())) out.add("zmiana");
            if ("przelacznik".startsWith(args[1].toLowerCase())) out.add("przełącznik");
            if ("głowka".startsWith(args[1].toLowerCase()) || "glowka".startsWith(args[1].toLowerCase())) out.add("głowka");
            return out;
        }
        // /stojak funkcja zmiana <Tab> - nazwy zapisanych presetów (stan A / stan B / off)
        if (first.equals("funkcja") && args.length >= 3
                && (args[1].equalsIgnoreCase("zmiana") || args[1].equalsIgnoreCase("stan")
                || args[1].equalsIgnoreCase("przelacznik") || args[1].equalsIgnoreCase("przełącznik"))) {
            List<String> out = new ArrayList<>();
            if (args.length == 3) {
                if ("off".startsWith(args[2].toLowerCase())) out.add("off");
            }
            for (String name : plugin.getStandPresetManager().listPresets()) {
                if (name.startsWith(args[args.length - 1].toLowerCase())) out.add(name);
            }
            return out;
        }
        // /stojak funkcja tv [szerokość] [wysokość] <Tab> - liczby
        if (first.equals("funkcja") && args.length >= 3 && args.length <= 4
                && (args[1].equalsIgnoreCase("tv") || args[1].equalsIgnoreCase("telewizor")
                || args[1].equalsIgnoreCase("ekran"))) {
            return List.of("1", "2", "3", "4", "5");
        }
        // /stojak funkcja lampa [wysokość] <Tab> - liczby
        if (first.equals("funkcja") && args.length == 3
                && (args[1].equalsIgnoreCase("lampa") || args[1].equalsIgnoreCase("lamp")
                || args[1].equalsIgnoreCase("latarnia"))) {
            return List.of("1", "2", "3", "4");
        }
        return options;
    }

    /**
     * Materiały (bloki i przedmioty) pasujące do wpisanego prefiksu - do podpowiedzi
     * argumentów <blok|przedmiot> / <materiał>. Limit 80 (tyle mieści lista podpowiedzi
     * w grze); filtrowanie po prefiksie zawęża listę w trakcie pisania.
     */
    private List<String> materialSuggestions(String partial) {
        String p = partial == null ? "" : partial.toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Material m : Material.values()) {
            String n = m.name().toLowerCase(java.util.Locale.ROOT);
            if (n.startsWith(p)) out.add(n);
            if (out.size() >= 80) break;
        }
        return out;
    }

    /** Współrzędne bloku, na który patrzy gracz (X, wierzch bloku Y, Z). */
    private String[] targetCoords(CommandSender sender) {
        if (!(sender instanceof Player player)) return null;
        org.bukkit.block.Block block = player.getTargetBlockExact(100);
        if (block == null) return null;
        return new String[]{
                String.valueOf(block.getX()),
                String.valueOf(block.getY() + 1),
                String.valueOf(block.getZ())
        };
    }

    /** Podpowiedź pojedynczej współrzędnej (z filtrowaniem po wpisanym prefiksie). */
    private List<String> filter(String coord, String partial) {
        if (partial == null || partial.isEmpty() || coord.startsWith(partial)) {
            return java.util.Collections.singletonList(coord);
        }
        return java.util.Collections.emptyList();
    }
}
