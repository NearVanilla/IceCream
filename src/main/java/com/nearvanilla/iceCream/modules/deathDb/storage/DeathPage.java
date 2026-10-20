package com.nearvanilla.iceCream.modules.deathDb.storage;

import java.util.List;

/**
 * One page of a player's deaths, newest first.
 *
 * @param entries the deaths on this page
 * @param page the zero based page index actually returned (after clamping)
 * @param pageCount total number of pages, always at least 1
 * @param totalRecords total number of stored deaths for the player
 */
public record DeathPage(List<DeathSummary> entries, int page, int pageCount, int totalRecords) {}
