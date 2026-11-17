package com.nearvanilla.iceCream.testutil;

/**
 * Shared configuration for tests of the wanderful module, which reads two wand definitions from the
 * plugin configuration.
 */
public final class WanderfulTestUtils {
  /**
   * The configuration of an enabled wanderful module with both wands defined. It matches the
   * defaults the plugin ships, with each value spelled out so a test can assert on it.
   */
  public static final String CONFIG =
      """
      modules:
        wanderful:
          enabled: true
          item_frame:
            type: WOODEN_SWORD
            name: '<gold>Item Frame Wand</gold>'
            lore:
              - '<dark_green>Now you see me,</dark_green>'
              - '<dark_red>now you don''t</dark_red>'
            crafting:
              shape:
                - D
                - I
                - S
              ingredients:
                D: DIAMOND
                I: ITEM_FRAME
                S: STICK
          armor_stand:
            type: WOODEN_HOE
            name: '<gold>Armor Stand Wand</gold>'
            lore:
              - Controlling the moves!
            crafting:
              shape:
                - D
                - A
                - H
              ingredients:
                D: DIAMOND
                A: ARMOR_STAND
                H: STICK
      """;

  private WanderfulTestUtils() {}
}
