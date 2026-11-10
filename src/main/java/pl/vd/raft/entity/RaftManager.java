package pl.vd.raft.entity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * RaftManager - zarządza wszystkimi tratwami i systemem wiatru.
 * 
 * Funkcjonalność:
 * - Lista aktywnych tratw
 * - System wiatru (kierunek i prędkość zmieniają się co X minut; wiatr napędza
 *   tratwę z rozwiniętym żaglem — patrz RaftPhysicsEngine.computeDrive)
 * - Tick handler (co tick aktualizuje wszystkie tratwy)
 * - Spawn/destruction tratw
 */
public class RaftManager {
    
    private static RaftManager instance;
    
    private final Map<UUID, RaftEntity> rafts;           // Aktywne tratwy
    private Vector currentDirection;                     // Aktualny kierunek prądu wody
    private double currentSpeed = 0.05;                  // Prędkość prądu wody
    
    // ─── Globalny wiatr i prąd ───
    private Vector globalWind = new Vector(0, 0, 0);     // Globalny wektor wiatru (kierunek * prędkość)
    private Vector globalCurrent = new Vector(0, 0, 0);  // Globalny wektor prądu
    private long currentChangeInterval = 9600;           // Co ile ticków zmienia się prąd (9600 = 8 min)
    private long lastCurrentChange = 0;                  // Ostatnia zmiana prądu
    private long windChangeInterval = 2400;              // Co ile ticków zmienia się wiatr (2400 = 2 min)
    private long lastWindChange = 0;                     // Ostatnia zmiana wiatru
    private Vector windDirection = new Vector(1, 0, 0);  // Aktualny kierunek wiatru (w poziomie)
    private double windSpeed = WIND_SPEED_MIN;           // Aktualna prędkość wiatru (bloki/sekundę)
    private boolean enabled = true;                      // Czy system jest włączony

    /** Zakres prędkości prądu (spokojny dryf — wygodny do chodzenia po tratwie). */
    private static final double CURRENT_SPEED_MIN = 1.0;
    private static final double CURRENT_SPEED_MAX = 1.4;

    /**
     * Zakres prędkości wiatru (bloki/sekundę). Wiatr jest szybszy od prądu, ale
     * tratwa nie przekroczy swojego maximumSpeed (RaftState = 1.5 bl/s), więc
     * pod żaglem płynie się po prostu z pełną prędkością tratwy.
     */
    private static final double WIND_SPEED_MIN = 1.4;
    private static final double WIND_SPEED_MAX = 1.9;

    /** Faza 2: co ile ticków sprzątać osierocone encje tratw (1200 = 60 s). */
    private static final int ORPHAN_SWEEP_INTERVAL = 1200;
    /** Faza 3: autosave stanu tratw co ile ticków (1200 = 60 s) — ochrona przed awarią. */
    private static final int AUTOSAVE_INTERVAL = 1200;
    /** Licznik ticków managera (do okresowego sprzątania i autosave). */
    private long tickCounter = 0;
    
    private RaftManager() {
        this.rafts = new HashMap<>();
        this.currentDirection = new Vector(1, 0, 0); // Początkowy kierunek: wschód

        // Wiatr od razu (nie dopiero po pierwszej zmianie co 2 min) — tratwa
        // z rozwiniętym żaglem ma napęd od pierwszej chwili po starcie.
        changeWindDirection();

        // Uruchom tick handler
        startTickHandler();
    }
    
    public static RaftManager getInstance() {
        if (instance == null) {
            instance = new RaftManager();
        }
        return instance;
    }
    
    /**
     * Tick handler - aktualizuje wszystkie tratwy co tick.
     */
    private void startTickHandler() {
        Bukkit.getScheduler().runTaskTimer(Bukkit.getPluginManager().getPlugin("ServerPlugin"), () -> {
            if (!enabled) return;
            
            // 1. Sprawdź czy zmienić prąd wody
            if (System.currentTimeMillis() - lastCurrentChange > currentChangeInterval * 50) { // 50ms per tick
                changeCurrentDirection();
                lastCurrentChange = System.currentTimeMillis();
            }

            // 1a. Sprawdź czy zmienić wiatr — wiatr zmienia się NIEZALEŻNIE od
            //     prądu (co 2 min). Od wiatru zależy napęd tratwy z rozwiniętym
            //     żaglem (RaftState.isSailing → RaftPhysicsEngine).
            if (System.currentTimeMillis() - lastWindChange > windChangeInterval * 50) {
                changeWindDirection();
                lastWindChange = System.currentTimeMillis();
            }

            // 1b. Faza 2 — okresowo sprzątaj osierocone encje po tratwach,
            //     których instancja już nie istnieje (awaria/restart/destroy w
            //     rozładowanym chunku), a Faza 3 — autosave stanu tratw (żeby
            //     restart serwera nie cofał postępu o więcej niż minutę).
            if (++tickCounter % ORPHAN_SWEEP_INTERVAL == 0) {
                sweepOrphanRaftEntities();
            }
            if (tickCounter % AUTOSAVE_INTERVAL == 0) {
                RaftPersistence.saveAll(rafts.values());
                // Faza 0 projektu anten: urządzenia też przeżywają awarię (60 s jak tratwy).
                pl.vd.raft.RaftPlugin raft = pl.vd.raft.RaftPlugin.getInstance();
                if (raft != null) {
                    pl.vd.raft.blocks.DevicePersistence.saveAll(raft.getDeviceRegistry());
                }
            }

            // 2. Aktualizuj wszystkie tratwy
            for (RaftEntity raft : new ArrayList<>(rafts.values())) {
                try {
                    // Podłącz globalny wiatr (0) i prąd do silnika fizyki
                    raft.getPhysicsEngine().setGlobalWind(globalWind);
                    raft.getPhysicsEngine().setGlobalCurrent(globalCurrent);
                    
                    // Wywołaj tick tratwy
                    raft.tick();
                } catch (Exception e) {
                    // Nie niszcz tratwy przy pojedynczym błędzie (np. chwilowy problem
                    // z chunkiem) — zaloguj i kontynuuj w nastepnym ticku.
                    org.bukkit.Bukkit.getLogger().warning(
                            "[Raft] Błąd ticka tratwy " + raft.getRaftId() + ": " + e);
                }
            }
        }, 0L, 1L); // Co tick
    }
    
    /**
     * Zmienia kierunek prądu wody (losowo).
     */
    private void changeCurrentDirection() {
        // Losowy kierunek w poziomie
        double angle = Math.random() * Math.PI * 2;
        currentDirection = new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize();

        // Losowa prędkość prądu: 1.0 - 1.4 bloków/sekundę (widoczny, ale wygodny ruch)
        currentSpeed = CURRENT_SPEED_MIN + Math.random() * (CURRENT_SPEED_MAX - CURRENT_SPEED_MIN);

        // Zaktualizuj globalCurrent (wektor prądu = kierunek * prędkość)
        globalCurrent = currentDirection.clone().multiply(currentSpeed);
    }

    /**
     * Zmienia kierunek i prędkość wiatru (losowo, w poziomie).
     *
     * Wiatr napędza tratwę TYLKO przy ROZWINIĘTYM żaglu (RaftState.isSailing)
     * — wtedy RaftPhysicsEngine bierze z globalWind kierunek i siłę napędu,
     * a nie z prądu wody. Przy zwiniętym żaglu tratwa dryfuje z prądem.
     */
    private void changeWindDirection() {
        double angle = Math.random() * Math.PI * 2;
        windDirection = new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize();
        windSpeed = WIND_SPEED_MIN + Math.random() * (WIND_SPEED_MAX - WIND_SPEED_MIN);
        globalWind = windDirection.clone().multiply(windSpeed);
    }
    

    
    /**
     * Tworzy nową tratwę na podanej pozycji.
     */
    public RaftEntity createRaft(Location location) {
        return createRaft(location, null);
    }

    /**
     * Tworzy nową tratwę na podanej pozycji z właścicielem.
     * Zwraca null, gdy świat osiągnął limit tratw (raft-max-rafts-per-world).
     */
    public RaftEntity createRaft(Location location, UUID owner) {
        int cap = maxRaftsPerWorld();
        if (cap > 0 && location.getWorld() != null
                && countRaftsInWorld(location.getWorld()) >= cap) {
            org.bukkit.Bukkit.getLogger().warning(
                    "[Raft] Limit tratw w świecie " + location.getWorld().getName()
                    + " osiągnięty (" + cap + ") — nie tworzę kolejnej tratwy.");
            return null;
        }
        return new RaftEntity(location.getWorld(), location, currentDirection, owner);
    }

    /** Ile tratw jest aktualnie zarejestrowanych w danym świecie. */
    private int countRaftsInWorld(org.bukkit.World world) {
        int n = 0;
        for (RaftEntity raft : rafts.values()) {
            if (raft.getState().getWorld().equals(world)) n++;
        }
        return n;
    }

    /** Limit tratw na świat z configu ServerPlugin (domyślnie 100). */
    private int maxRaftsPerWorld() {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("ServerPlugin");
        if (p == null || p.getConfig() == null) return 100;
        return p.getConfig().getInt("raft-max-rafts-per-world", 100);
    }

    /**
     * Zwraca tratwę po jej UUID (lub null). Używane przez filtr pakietów LOD.
     */
    public RaftEntity getRaft(UUID raftId) {
        return raftId == null ? null : rafts.get(raftId);
    }

    /**
     * Sprząta osierocone encje tratw (Faza 2): shulkery-kolizje, markery i deski
     * BlockDisplay z tagiem "raft_<uuid>", których tratwa NIE istnieje w rejestrze
     * (po awarii/restarcie albo zniszczeniu tratwy w rozładowanym chunku), a także
     * „duplikaty wędrujące" — encje żywej tratwy, które stoją daleko POZA jej
     * pokładem (mogą powstać po awarii, gdy tratwa zdryfowała między autosave'ami).
     * Encje żywych tratw stojące przy pokładzie nigdy nie są ruszane.
     * Skanujemy tylko światy, w których tratwy występują (światy zarejestrowanych
     * tratw + świat Raft z configu) — nie cały serwer.
     */
    private void sweepOrphanRaftEntities() {
        Set<org.bukkit.World> worlds = new HashSet<>();
        for (RaftEntity raft : rafts.values()) {
            worlds.add(raft.getState().getWorld());
        }
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("ServerPlugin");
        if (p != null && p.getConfig() != null) {
            org.bukkit.World raftWorld = Bukkit.getWorld(p.getConfig().getString("raft-world-name", "RAFT"));
            if (raftWorld != null) worlds.add(raftWorld);
        }
        if (worlds.isEmpty()) return;

        int removed = 0;
        // Obrysy pokładów zarejestrowanych tratw — liczone raz na przebieg.
        Map<UUID, double[]> boundsCache = new HashMap<>();
        for (org.bukkit.World world : worlds) {
            for (Class<? extends org.bukkit.entity.Entity> type : List.of(
                    org.bukkit.entity.Shulker.class,
                    org.bukkit.entity.ArmorStand.class,
                    org.bukkit.entity.BlockDisplay.class,
                    org.bukkit.entity.ItemDisplay.class,
                    org.bukkit.entity.TextDisplay.class)) {
                for (org.bukkit.entity.Entity e : world.getEntitiesByClass(type)) {
                    if (e.isDead() || !e.isValid()) continue;
                    UUID raftId = RaftEntity.raftIdOf(e);
                    if (raftId == null) continue;

                    RaftEntity raft = rafts.get(raftId);
                    if (raft == null) {
                        // Osierocona encja po nieistniejącej tratwie — usuń.
                        e.remove();
                        removed++;
                        continue;
                    }
                    // Encja żywej tratwy: trzymamy tylko te przy jej pokładzie.
                    // Daleki duplikat (po awarii/dryfie) to śmieć — usuń.
                    double[] bounds = boundsCache.get(raftId);
                    if (bounds == null) {
                        bounds = raft.deckBoundsWorldXZ();
                        boundsCache.put(raftId, bounds);
                    }
                    if (bounds == null || !isNearDeck(raft, e, bounds)) {
                        e.remove();
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) {
            org.bukkit.Bukkit.getLogger().warning(
                    "[Raft] Sprzątanie: usunięto " + removed
                    + " osieroconych/zbłąkanych encji tratw.");
        }
    }

    /** Czy encja stoi przy pokładzie tratwy (w obrysie XZ + margines i podobnej Y). */
    private static boolean isNearDeck(RaftEntity raft, org.bukkit.entity.Entity e, double[] bounds) {
        double margin = 6.0;
        org.bukkit.Location loc = e.getLocation();
        double dx = Math.max(bounds[0] - loc.getX(), Math.max(loc.getX() - bounds[1], 0));
        double dz = Math.max(bounds[2] - loc.getZ(), Math.max(loc.getZ() - bounds[3], 0));
        if (dx * dx + dz * dz > margin * margin) return false;
        double raftY = raft.getState().getPosition().getY();
        return Math.abs(loc.getY() - raftY) <= margin;
    }

    /**
     * Tworzy tratwę najbliżej gracza na powierzchni wody.
     * Jeśli gracz MA JUŻ tratwę — zwraca ją zamiast spawnować drugą
     * (inaczej tratwy gromadzą się przy spawnie w jeden stos).
     * Zwraca null jeśli w promieniu nie ma wody.
     */
    public RaftEntity spawnRaftForPlayer(org.bukkit.entity.Player player, int radius) {
        RaftEntity existing = getRaftForPlayer(player.getUniqueId());
        if (existing != null) return existing;

        // Faza 3: jeśli gracz ma zapisaną tratwę — przywróć ją w miejscu,
        // w którym ją zostawił (zamiast spawnować nową 2×2 przy graczu).
        RaftEntity restored = restoreRaftForPlayer(player.getUniqueId());
        if (restored != null) return restored;

        Location water = findWaterSurface(player.getLocation(), radius);
        if (water == null) return null;
        return createRaft(water, player.getUniqueId());
    }

    /**
     * Przywraca zapisaną tratwę gracza (Faza 3) w miejscu, w którym została.
     * Używa zapisanego raftId — dzięki temu przejmuje kolizje (shulkery/markery),
     * które przetrwały w chunkach, zamiast tworzyć duplikaty.
     * Zwraca null, gdy gracz nie ma zapisu albo świat z zapisu nie istnieje.
     */
    public RaftEntity restoreRaftForPlayer(UUID owner) {
        if (owner == null) return null;
        RaftEntity existing = getRaftForPlayer(owner);
        if (existing != null) return existing;

        RaftPersistence.SaveData save = RaftPersistence.load(owner);
        if (save == null || save.raftId == null) return null;

        RaftEntity byId = rafts.get(save.raftId);
        if (byId != null) return byId;

        org.bukkit.World world = Bukkit.getWorld(save.world);
        if (world == null) {
            org.bukkit.Bukkit.getLogger().warning(
                    "[Raft] Świat zapisu tratwy '" + save.world
                    + "' nie istnieje — tratwa gracza " + owner + " nie zostanie przywrócona.");
            return null;
        }
        return new RaftEntity(world,
                new Location(world, save.x, save.y, save.z),
                currentDirection, owner, save);
    }

    /**
     * Zwraca tratwę należącą do gracza (lub null).
     */
    public RaftEntity getRaftForPlayer(UUID owner) {
        for (RaftEntity raft : new ArrayList<>(rafts.values())) {
            if (owner.equals(raft.getOwner())) return raft;
        }
        return null;
    }

    /**
     * Usuwa tratwę należącą do gracza (świadome usunięcie).
     * Kasuje też zapis tratwy — inaczej tratwa „wskrzeszałaby" po powrocie gracza.
     */
    public void removeRaftForPlayer(UUID owner) {
        RaftEntity raft = getRaftForPlayer(owner);
        if (raft != null) {
            RaftPersistence.delete(owner);
            raft.destroy();
        }
    }

    /**
     * Szuka najbliższej powierzchni wody w promieniu `radius` bloków.
     * Zwraca pozycję NA powierzchni wody (centrum Y+1, tratwa pływa), lub null.
     */
    public static Location findWaterSurface(Location center, int radius) {
        org.bukkit.World world = center.getWorld();
        if (world == null) return null;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int bestDist = Integer.MAX_VALUE;
        Location best = null;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int bx = cx + dx;
                int bz = cz + dz;

                // Szukaj kolumny wody od góry do dołu
                for (int dy = radius; dy >= -radius; dy--) {
                    int by = cy + dy;
                    org.bukkit.block.Block block = world.getBlockAt(bx, by, bz);
                    if (block.getType() == org.bukkit.Material.WATER) {
                        // Powierzchnia = blok nad wodą jest pusty
                        org.bukkit.block.Block above = world.getBlockAt(bx, by + 1, bz);
                        if (above.getType() == org.bukkit.Material.AIR
                                || above.getType() == org.bukkit.Material.CAVE_AIR) {
                            int dist = dx * dx + dy * dy + dz * dz;
                            if (dist < bestDist) {
                                bestDist = dist;
                                // +0.5 na srodek XZ; srodek tratwy od razu na pozycji
                                // rownowagi (powierzchnia + FLOAT_OFFSET) — dzieki temu
                                // tratwa NIE „wyskakuje” po postawieniu, tylko od razu
                                // plywa na docelowej glebokosci (6/10 bloka w wodzie)
                                best = new Location(world, bx + 0.5,
                                        by + 1.0 + RaftEntity.FLOAT_OFFSET, bz + 0.5);
                            }
                        }
                        break; // nie szukaj glebiej w tej kolumnie
                    }
                }
            }
        }
        return best;
    }
    
    /**
     * Usuwa tratwę (świadome usunięcie, np. /raft remove). Kasuje zapis właściciela.
     */
    public void removeRaft(UUID raftId) {
        RaftEntity raft = rafts.remove(raftId);
        if (raft != null) {
            if (raft.getOwner() != null) {
                RaftPersistence.delete(raft.getOwner());
            }
            raft.destroy();
        }
    }
    
    /**
     * Rejestruje tratwę (wywoływane z RaftEntity).
     */
    public void registerRaft(RaftEntity raft) {
        rafts.put(raft.getRaftId(), raft);
    }
    
    /**
     * Wyrejestrowuje tratwę.
     */
    public void unregisterRaft(RaftEntity raft) {
        rafts.remove(raft.getRaftId());
    }
    
    /**
     * Znajduje tratwę najbliżej gracza (tylko w JEGO świecie — getLocation().distance()
     * między światami rzuca wyjątek, a tratwy z innego świata i tak nie są "najbliżej").
     */
    public RaftEntity getNearestRaft(Player player, double maxDistance) {
        RaftEntity nearest = null;
        double nearestDistance = maxDistance;

        for (RaftEntity raft : rafts.values()) {
            if (player.getWorld() != raft.getState().getWorld()) continue;
            double distance = player.getLocation().distance(raft.getCenter());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = raft;
            }
        }
        
        return nearest;
    }
    
    // ─────────────── Globalny wiatr/prąd (plugin.jar) ───────────────

    public Vector getGlobalWind() { return globalWind.clone(); }

    /** Ustawia wiatr ręcznie (diagnostyka). Kierunek/moc pól liczone z wektora. */
    public void setGlobalWind(Vector wind) {
        this.globalWind = wind != null ? wind.clone() : new Vector(0, 0, 0);
        double len = this.globalWind.length();
        if (len > 1e-6) {
            this.windDirection = this.globalWind.clone().normalize();
            this.windSpeed = len;
        } else {
            this.windSpeed = 0;
        }
    }

    public Vector getGlobalCurrent() { return globalCurrent.clone(); }
    public void setGlobalCurrent(Vector current) {
        this.globalCurrent = current != null ? current.clone() : new Vector(0, 0, 0);
    }

    // Gettery/Settery
    public Vector getCurrentDirection() { return currentDirection.clone(); }
    public double getCurrentSpeed() { return currentSpeed; }
    public Collection<RaftEntity> getRafts() { return Collections.unmodifiableCollection(rafts.values()); }
    public int getRaftCount() { return rafts.size(); }
    
    public void setCurrentChangeInterval(long interval) { this.currentChangeInterval = interval; }
    public void setWindChangeInterval(long interval) { this.windChangeInterval = Math.max(1, interval); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
