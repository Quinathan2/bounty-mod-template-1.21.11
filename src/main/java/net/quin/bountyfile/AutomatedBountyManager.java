package net.quin.bountyfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutomatedBountyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutomatedBountyManager");
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Configurable fields
    public static int intervalMinutes = 5;       // default 5 minutes
    public static int minAmount = 5;             // default min diamonds
    public static int maxAmount = 50;            // default max diamonds
    public static boolean anonymous = true;      // default anonymous

    private static MinecraftServer server;

    public static void start(MinecraftServer serverInstance) {
        server = serverInstance;
        scheduleNext(); // starts the automated loop
    }

    private static void scheduleNext() {
        scheduler.schedule(() -> server.execute(() -> {
            BountyCommandManager.placeRandomBountyServer(server, minAmount, maxAmount, anonymous);

            LOGGER.info("Automated bounty placed (min " + minAmount + ", max " + maxAmount +
                    ", anonymous " + anonymous + "). Next in " + intervalMinutes + " min.");

            scheduleNext(); // reschedule next run
        }), intervalMinutes, TimeUnit.MINUTES);
    }

    // In-game setter methods
    public static void setInterval(int minutes) {
        intervalMinutes = minutes;
    }

    public static void setMinMax(int min, int max) {
        minAmount = min;
        maxAmount = max;
    }

    public static void setAnonymous(boolean value) {
        anonymous = value;
    }

}
