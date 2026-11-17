package com.nearvanilla.iceCream.testutil;

import com.nearvanilla.iceCream.modules.onePlayerSleep.OnePlayerSleepModule;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Helpers for testing the one player sleep module with MockBukkit, which can neither put a player
 * to bed nor wake one, and whose world clock never reacts to being written.
 */
public final class SleepTestUtils {
  private static final String WAKE_COMMAND = "/oneplayersleep wake ";

  private SleepTestUtils() {}

  /**
   * Adds a player that can be put to bed and woken again. MockBukkit throws {@code
   * UnimplementedOperationException} from {@code wakeup(...)}, so this player overrides it to clear
   * the sleeping flag, and counts the wake calls it receives.
   *
   * @param server the mock server
   * @param name the player name
   * @return the added player
   */
  public static SleepyPlayer addPlayer(ServerMock server, String name) {
    SleepyPlayer player = new SleepyPlayer(server, name);
    server.addPlayer(player);
    return player;
  }

  /**
   * Adds a world whose clock a test controls, set to the given time.
   *
   * @param server the mock server
   * @param name the world name
   * @param fullTime the time to start at, in ticks
   * @return the added world, in {@link ClockWorld.WriteMode#ANNOUNCE} mode
   */
  public static ClockWorld addWorld(ServerMock server, String name, long fullTime) {
    ClockWorld world = new ClockWorld();
    world.setName(name);
    world.setClock(fullTime);
    server.addWorld(world);
    return world;
  }

  /**
   * Finds the "[Wake up]" button in a "has gone to sleep" announcement.
   *
   * @param announcement the message a player received
   * @return the part of the message that runs the wake command when clicked, or null when there is
   *     none
   */
  public static Component wakeButton(Component announcement) {
    if (announcement == null) {
      return null;
    }
    if (announcement.clickEvent() != null
        && announcement.clickEvent().payload() instanceof ClickEvent.Payload.Text text
        && text.value().startsWith(WAKE_COMMAND)) {
      return announcement;
    }
    for (Component child : announcement.children()) {
      Component button = wakeButton(child);
      if (button != null) {
        return button;
      }
    }
    return null;
  }

  /**
   * Reads the token out of a "has gone to sleep" announcement, which carries it in the command of
   * the "[Wake up]" button.
   *
   * @param announcement the message a player received
   * @return the token, or null when the message has no wake button
   */
  public static String wakeToken(Component announcement) {
    Component button = wakeButton(announcement);
    if (button == null) {
      return null;
    }
    ClickEvent.Payload.Text command = (ClickEvent.Payload.Text) button.clickEvent().payload();
    return command.value().substring(WAKE_COMMAND.length());
  }

  /**
   * Makes the module's wake-up cooldown look as if it started the given time ago, without waiting.
   * The module measures the cooldown with {@code System.nanoTime()} and offers no way to pass the
   * time in, so this moves the recorded start instead.
   *
   * @param module the module that is cooling down
   * @param age how long ago the cooldown started
   */
  public static void ageCooldown(OnePlayerSleepModule module, Duration age) {
    try {
      Field started = OnePlayerSleepModule.class.getDeclaredField("cooldownStartedNanos");
      started.setAccessible(true);
      started.setLong(module, System.nanoTime() - age.toNanos());
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Could not age the cooldown", exception);
    }
  }

  /** A player that can be put to bed and woken, and that can be made to refuse waking. */
  public static final class SleepyPlayer extends PlayerMock {
    private boolean staysInBed;
    private int wakeCalls;

    private SleepyPlayer(ServerMock server, String name) {
      super(server, name, UUID.randomUUID());
    }

    /** Puts the player to bed, as if they had entered one and fallen asleep. */
    public void goToBed() {
      setSleeping(true);
    }

    /**
     * Makes {@code wakeup} do nothing, like a plugin cancelling the bed leave.
     *
     * @param staysInBed true to stay asleep when told to wake
     */
    public void refuseToWake(boolean staysInBed) {
      this.staysInBed = staysInBed;
    }

    /**
     * Returns how many times the player was told to wake up.
     *
     * @return the number of {@code wakeup} calls
     */
    public int wakeCalls() {
      return wakeCalls;
    }

    @Override
    public void wakeup(boolean setSpawnLocation) {
      wakeCalls++;
      if (!staysInBed) {
        setSleeping(false);
      }
    }
  }

  /**
   * A world whose clock a test controls. It advances one tick per scheduler tick, like MockBukkit's
   * own worlds, and the test can set it silently, as the passing of time would, while the module's
   * writes announce a custom {@link TimeSkipEvent} the way Paper does. Those writes can also be
   * made to fail.
   */
  public static final class ClockWorld extends WorldMock {
    /** What {@link ClockWorld#setFullTime(long)} does. */
    public enum WriteMode {
      /** Raises a custom {@link TimeSkipEvent} and then sets the time, like Paper. */
      ANNOUNCE,
      /** Leaves the time as it was, like a plugin cancelling the time skip. */
      IGNORE,
      /** Throws {@link IllegalArgumentException}, like a server refusing the time. */
      REJECT
    }

    private WriteMode mode = WriteMode.ANNOUNCE;
    private ClockWorld clockSource;
    private long clock;

    private ClockWorld() {}

    /**
     * Makes this world read and write the clock of another world, like worlds that share a clock on
     * a real server. The source world keeps advancing the clock; this world no longer ticks it.
     *
     * @param source the world whose clock this world uses
     */
    public void shareClockWith(ClockWorld source) {
      this.clockSource = source;
    }

    /**
     * Chooses what writes to the clock do.
     *
     * @param mode the behaviour of {@link #setFullTime(long)}
     */
    public void writeMode(WriteMode mode) {
      this.mode = mode;
    }

    /**
     * Sets the clock without raising an event, as time passing naturally would.
     *
     * @param fullTime the new time in ticks
     */
    public void setClock(long fullTime) {
      if (clockSource != null) {
        clockSource.setClock(fullTime);
      } else {
        clock = fullTime;
      }
    }

    @Override
    public long getFullTime() {
      return clockSource != null ? clockSource.getFullTime() : clock;
    }

    @Override
    public void tick() {
      super.tick();
      if (clockSource == null) {
        clock++;
      }
    }

    @Override
    public void setFullTime(long fullTime) {
      switch (mode) {
        case ANNOUNCE -> {
          TimeSkipEvent event =
              new TimeSkipEvent(
                  this, ClockTimeSkipEvent.SkipReason.CUSTOM, fullTime - getFullTime());
          event.callEvent();
          if (!event.isCancelled()) {
            setClock(getFullTime() + event.getSkipAmount());
          }
        }
        case IGNORE -> {}
        case REJECT -> throw new IllegalArgumentException("Refused time " + fullTime);
      }
    }
  }
}
