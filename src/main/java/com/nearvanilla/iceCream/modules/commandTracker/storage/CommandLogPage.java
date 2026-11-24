package com.nearvanilla.iceCream.modules.commandTracker.storage;

import java.util.List;

/**
 * One page of stored commands, newest first.
 *
 * @param entries the commands on this page
 * @param page the zero based page index actually returned (after clamping)
 * @param pageCount total number of pages, always at least 1
 * @param totalEntries total number of commands matching the query
 */
public record CommandLogPage(
    List<CommandEntry> entries, int page, int pageCount, int totalEntries) {}
